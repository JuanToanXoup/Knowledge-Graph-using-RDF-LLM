"""
FastAPI Backend for Knowledge Graph System
Provides REST API endpoints for graph creation and querying
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict
import os
import shutil
from datetime import datetime
import uuid
import json

from document_processor import DocumentProcessor
from text_preprocessor import TextPreprocessor
from entity_extractor import EntityExtractor
from relation_extractor import RelationExtractor
from graph_builder import KnowledgeGraphBuilder
from graph_visualizer import GraphVisualizer
from graph_querier import KnowledgeGraphQuerier
from semantic_retriever import SemanticRetriever
from config import OPENAI_API_KEY

# Initialize FastAPI app
app = FastAPI(
    title="Knowledge Graph API",
    description="API for creating and querying knowledge graphs from documents",
    version="1.0.0"
)

# Add CORS middleware for Streamlit
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global storage for active knowledge graphs
active_graphs: Dict[str, Dict] = {}

# Global storage for chat history
chat_history: Dict[str, List[Dict]] = {}

# Pydantic models for request/response
class GraphCreationResponse(BaseModel):
    graph_id: str
    message: str
    entities_count: int
    relations_count: int
    statistics: Dict
    output_dir: str

class SemanticSearchRequest(BaseModel):
    graph_id: str
    query: str
    top_k: int = 5

class QuestionAnswerRequest(BaseModel):
    graph_id: str
    question: str

class EntityRelationsRequest(BaseModel):
    graph_id: str
    entity_name: str

class SPARQLQueryRequest(BaseModel):
    graph_id: str
    query: str

class ChatMessage(BaseModel):
    role: str
    content: str
    timestamp: str
    facts: Optional[List[str]] = None

class SaveChatRequest(BaseModel):
    graph_id: str
    messages: List[ChatMessage]


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "message": "Knowledge Graph API",
        "version": "1.0.0",
        "endpoints": {
            "health": "/health",
            "upload": "/upload",
            "graphs": "/graphs",
            "semantic_search": "/semantic_search",
            "question_answer": "/question_answer",
            "entity_relations": "/entity_relations",
            "sparql_query": "/sparql_query",
            "visualization": "/visualization/{graph_id}",
            "download_graph": "/download_graph/{graph_id}"
        }
    }


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "active_graphs": len(active_graphs),
        "openai_configured": bool(OPENAI_API_KEY)
    }


@app.post("/upload", response_model=GraphCreationResponse)
async def upload_document(
    file: UploadFile = File(...),
    background_tasks: BackgroundTasks = None
):
    """
    Upload a document and create a knowledge graph
    Supports PDF, TXT, and DOCX files
    """
    
    # Validate file type
    allowed_extensions = {'.pdf', '.txt', '.docx', '.doc'}
    file_ext = os.path.splitext(file.filename)[1].lower()
    
    if file_ext not in allowed_extensions:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type: {file_ext}. Allowed: {allowed_extensions}"
        )
    
    # Generate unique ID
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    graph_id = f"{timestamp}_{uuid.uuid4().hex[:8]}"
    output_dir = f"output/kg_{graph_id}"
    os.makedirs(output_dir, exist_ok=True)
    
    # Save uploaded file temporarily
    temp_file_path = f"{output_dir}/uploaded_{file.filename}"
    
    try:
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        
        # Process document
        doc_processor = DocumentProcessor()
        text = doc_processor.load_document(temp_file_path)
        
        upload_progress[graph_id].update({
            'progress': 20,
            'message': 'Document loaded, preprocessing text...',
            'logs': upload_progress[graph_id]['logs'] + [f"✓ Loaded document from {temp_file_path}"]
        })
        
        # Preprocess
        preprocessor = TextPreprocessor()
        preprocessed = preprocessor.preprocess(text)
        
        # Extract entities
        entity_extractor = EntityExtractor(openai_api_key=OPENAI_API_KEY)
        spacy_entities = entity_extractor.extract_entities_spacy(preprocessed['cleaned'])
        
        upload_progress[graph_id].update({
            'progress': 40,
            'message': f'Extracted {len(spacy_entities)} entities with spaCy, using LLM...',
            'logs': upload_progress[graph_id]['logs'] + [f"✓ Extracted {len(spacy_entities)} entities using spaCy"]
        })
        
        llm_entities = entity_extractor.extract_entities_llm(preprocessed['cleaned'])
        
        upload_progress[graph_id].update({
            'progress': 50,
            'message': 'Merging entities...',
            'logs': upload_progress[graph_id]['logs'] + [f"✓ Extracted {len(llm_entities)} entities using LLM"]
        })
        
        entities = entity_extractor.merge_entities(spacy_entities, llm_entities)
        
        upload_progress[graph_id].update({
            'progress': 60,
            'message': 'Extracting relations...',
            'logs': upload_progress[graph_id]['logs'] + [f"✓ Merged into {len(entities)} unique entities"]
        })
        
        # Extract relations
        relation_extractor = RelationExtractor(openai_api_key=OPENAI_API_KEY)
        pattern_relations = relation_extractor.extract_relations_pattern(
            preprocessed['cleaned'], entities
        )
        llm_relations = relation_extractor.extract_relations_llm(
            preprocessed['cleaned'], entities
        )
        relations = relation_extractor.merge_relations(pattern_relations, llm_relations)
        
        # Build knowledge graph
        kg_builder = KnowledgeGraphBuilder()
        kg_builder.build_from_extractions(entities, relations)
        
        upload_progress[graph_id].update({
            'progress': 85,
            'message': 'Saving graph...',
            'logs': upload_progress[graph_id]['logs'] + ["✓ Knowledge graph built"]
        })
        
        # Save graph
        kg_builder.save_rdf(f"{output_dir}/knowledge_graph.ttl")
        
        upload_progress[graph_id].update({
            'progress': 90,
            'message': 'Creating visualization...',
            'logs': upload_progress[graph_id]['logs'] + ["✓ Graph saved as RDF"]
        })
        
        # Create visualization
        visualizer = GraphVisualizer(kg_builder)
        visualizer.visualize(f"{output_dir}/knowledge_graph.png")
        
        upload_progress[graph_id].update({
            'progress': 95,
            'message': 'Indexing for search...',
            'logs': upload_progress[graph_id]['logs'] + ["✓ Visualization created"]
        })
        
        # Initialize semantic retriever
        retriever = SemanticRetriever(kg_builder)
        retriever.index_graph()
        
        # Initialize querier
        querier = KnowledgeGraphQuerier(kg_builder)
        
        # Get statistics
        stats = kg_builder.get_statistics()
        
        # Store in memory
        active_graphs[graph_id] = {
            'kg_builder': kg_builder,
            'retriever': retriever,
            'querier': querier,
            'entities': entities,
            'relations': relations,
            'output_dir': output_dir,
            'filename': file.filename,
            'created_at': datetime.now().isoformat(),
            'statistics': stats
        }
        
        # Save metadata
        metadata = {
            'graph_id': graph_id,
            'filename': file.filename,
            'created_at': datetime.now().isoformat(),
            'entities_count': len(entities),
            'relations_count': len(relations),
            'statistics': stats
        }
        
        with open(f"{output_dir}/metadata.json", 'w') as f:
            json.dump(metadata, f, indent=2)
        
        return GraphCreationResponse(
            graph_id=graph_id,
            message="Knowledge graph created successfully",
            entities_count=len(entities),
            relations_count=len(relations),
            statistics=stats,
            output_dir=output_dir
        )
        
    except Exception as e:
        # Cleanup on error
        if os.path.exists(output_dir):
            shutil.rmtree(output_dir)
        raise HTTPException(status_code=500, detail=str(e))
    
    finally:
        # Close uploaded file
        await file.close()


@app.get("/graphs")
async def list_graphs():
    """List all active knowledge graphs"""
    graphs_info = []
    
    for graph_id, data in active_graphs.items():
        graphs_info.append({
            'graph_id': graph_id,
            'filename': data.get('filename', 'Unknown'),
            'created_at': data['created_at'],
            'entities_count': len(data['entities']),
            'relations_count': len(data['relations']),
            'statistics': data['statistics']
        })
    
    return {"graphs": graphs_info, "total": len(graphs_info)}




@app.get("/chat_history/{graph_id}")
async def get_chat_history(graph_id: str):
    """Get chat history for a specific graph"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    return {
        "graph_id": graph_id,
        "messages": chat_history.get(graph_id, []),
        "count": len(chat_history.get(graph_id, []))
    }


@app.post("/chat_history/{graph_id}")
async def save_chat_message(graph_id: str, message: ChatMessage):
    """Save a chat message to history"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    if graph_id not in chat_history:
        chat_history[graph_id] = []
    
    chat_history[graph_id].append(message.dict())
    
    return {
        "message": "Chat message saved",
        "total_messages": len(chat_history[graph_id])
    }


@app.delete("/chat_history/{graph_id}")
async def clear_chat_history(graph_id: str):
    """Clear chat history for a specific graph"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    if graph_id in chat_history:
        del chat_history[graph_id]
    
    return {"message": "Chat history cleared"}


@app.get("/graph/{graph_id}")
async def get_graph_info(graph_id: str):
    """Get information about a specific graph"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    data = active_graphs[graph_id]
    
    return {
        'graph_id': graph_id,
        'filename': data['filename'],
        'created_at': data['created_at'],
        'entities_count': len(data['entities']),
        'relations_count': len(data['relations']),
        'statistics': data['statistics'],
        'sample_entities': [e['text'] for e in data['entities'][:10]]
    }


@app.post("/semantic_search")
async def semantic_search(request: SemanticSearchRequest):
    """Perform semantic search on knowledge graph"""
    if request.graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    retriever = active_graphs[request.graph_id]['retriever']
    
    try:
        results = retriever.search(request.query, top_k=request.top_k)
        return {"results": results, "query": request.query}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/question_answer")
async def question_answer(request: QuestionAnswerRequest):
    """Answer questions using LLM and knowledge graph"""
    if request.graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    if not OPENAI_API_KEY:
        raise HTTPException(
            status_code=400,
            detail="OpenAI API key not configured"
        )
    
    retriever = active_graphs[request.graph_id]['retriever']
    
    try:
        answer = retriever.answer_question_llm(request.question, OPENAI_API_KEY)
        
        # Also get relevant facts
        relevant_facts = retriever.search(request.question, top_k=5)
        
        return {
            "question": request.question,
            "answer": answer,
            "relevant_facts": relevant_facts
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/entity_relations")
async def entity_relations(request: EntityRelationsRequest):
    """Find all relations for a specific entity"""
    if request.graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    querier = active_graphs[request.graph_id]['querier']
    
    try:
        results = querier.find_entity_relations(request.entity_name)
        return {
            "entity": request.entity_name,
            "relations": results,
            "count": len(results)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/sparql_query")
async def sparql_query(request: SPARQLQueryRequest):
    """Execute custom SPARQL query"""
    if request.graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    querier = active_graphs[request.graph_id]['querier']
    
    try:
        results = querier.query(request.query)
        return {
            "results": results,
            "count": len(results)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Query execution failed: {str(e)}")


@app.get("/visualization/{graph_id}")
async def get_visualization(graph_id: str):
    """Get the knowledge graph visualization image"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    output_dir = active_graphs[graph_id]['output_dir']
    image_path = f"{output_dir}/knowledge_graph.png"
    
    if not os.path.exists(image_path):
        raise HTTPException(status_code=404, detail="Visualization not found")
    
    return FileResponse(image_path, media_type="image/png")


@app.get("/download_graph/{graph_id}")
async def download_graph(graph_id: str):
    """Download the RDF knowledge graph file"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    output_dir = active_graphs[graph_id]['output_dir']
    graph_path = f"{output_dir}/knowledge_graph.ttl"
    
    if not os.path.exists(graph_path):
        raise HTTPException(status_code=404, detail="Graph file not found")
    
    return FileResponse(
        graph_path,
        media_type="text/turtle",
        filename=f"knowledge_graph_{graph_id}.ttl"
    )


@app.get("/entities/{graph_id}")
async def get_entities(graph_id: str):
    """Get all entities from a graph"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    entities = active_graphs[graph_id]['entities']
    
    return {
        "entities": entities,
        "count": len(entities)
    }


@app.delete("/graph/{graph_id}")
async def delete_graph(graph_id: str):
    """Delete a knowledge graph from memory"""
    if graph_id not in active_graphs:
        raise HTTPException(status_code=404, detail="Graph not found")
    
    # Remove from memory
    del active_graphs[graph_id]
    
    return {"message": f"Graph {graph_id} deleted successfully"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)