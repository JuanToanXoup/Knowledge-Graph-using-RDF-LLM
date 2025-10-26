"""
Main Pipeline - Complete Knowledge Graph Creation Example
"""

import os
from document_processor import DocumentProcessor
from text_preprocessor import TextPreprocessor
from entity_extractor import EntityExtractor
from relation_extractor import RelationExtractor
from graph_builder import KnowledgeGraphBuilder
from graph_visualizer import GraphVisualizer
from graph_querier import KnowledgeGraphQuerier
from semantic_retriever import SemanticRetriever
from config import OPENAI_API_KEY

from datetime import datetime
import uuid


def interactive_query_interface(kg_builder: KnowledgeGraphBuilder, entities: list):
    """Interactive interface for querying the knowledge graph"""
    
    # Initialize querying tools
    print("\n" + "="*70)
    print("INITIALIZING QUERY INTERFACE")
    print("="*70)
    
    querier = KnowledgeGraphQuerier(kg_builder)
    retriever = SemanticRetriever(kg_builder)
    retriever.index_graph()
    
    print("✓ Query interface ready!")
    
    while True:
        print("\n" + "="*70)
        print("KNOWLEDGE GRAPH QUERY MENU")
        print("="*70)
        print("\nAvailable Options:")
        print("  1. Semantic Search - Find relevant information using natural language")
        print("  2. Question Answering - Ask questions and get AI-generated answers")
        print("  3. SPARQL Query - Find all relations for a specific entity")
        print("  4. Custom SPARQL Query - Execute your own SPARQL query")
        print("  5. View Graph Statistics")
        print("  6. Exit")
        
        choice = input("\nSelect an option (1-6): ").strip()
        
        if choice == '1':
            semantic_search_interface(retriever)
        
        elif choice == '2':
            question_answering_interface(retriever)
        
        elif choice == '3':
            sparql_entity_relations_interface(querier, entities)
        
        elif choice == '4':
            custom_sparql_interface(querier, kg_builder)
        
        elif choice == '5':
            show_graph_statistics(kg_builder)
        
        elif choice == '6':
            print("\n👋 Exiting query interface. Goodbye!")
            break
        
        else:
            print("\n❌ Invalid option. Please select 1-6.")


def semantic_search_interface(retriever: SemanticRetriever):
    """Interactive semantic search interface"""
    print("\n" + "-"*70)
    print("SEMANTIC SEARCH")
    print("-"*70)
    print("Search the knowledge graph using natural language.")
    
    while True:
        query = input("\nEnter search query (or 'back' to return): ").strip()
        
        if query.lower() in ['back', 'exit', 'quit']:
            break
        
        if not query:
            print("❌ Please enter a search query.")
            continue
        
        print(f"\n🔍 Searching for: '{query}'")
        
        top_k = input("Number of results to show (default 5): ").strip()
        top_k = int(top_k) if top_k.isdigit() else 5
        
        results = retriever.search(query, top_k=top_k)
        
        if not results:
            print("No results found.")
            continue
        
        print(f"\n📊 Top {len(results)} Results:")
        for i, result in enumerate(results, 1):
            print(f"\n  {i}. {result['subject']} → {result['predicate']} → {result['object']}")
            print(f"     Similarity Score: {result['similarity']:.3f}")
        
        another = input("\nSearch again? (y/n): ").strip().lower()
        if another != 'y':
            break


def question_answering_interface(retriever: SemanticRetriever):
    """Interactive question answering interface"""
    print("\n" + "-"*70)
    print("QUESTION ANSWERING (AI-Powered)")
    print("-"*70)
    
    if not OPENAI_API_KEY:
        print("❌ Question answering requires an OpenAI API key.")
        print("Please set OPENAI_API_KEY in your .env file.")
        input("\nPress Enter to continue...")
        return
    
    print("Ask questions about the knowledge graph and get AI-generated answers.")
    print("Example: 'What did Einstein work on?', 'Where was Marie Curie born?'")
    
    while True:
        question = input("\nEnter your question (or 'back' to return): ").strip()
        
        if question.lower() in ['back', 'exit', 'quit']:
            break
        
        if not question:
            print("❌ Please enter a question.")
            continue
        
        print(f"\n💭 Thinking about: '{question}'")
        print("⏳ Retrieving relevant facts and generating answer...")
        
        answer = retriever.answer_question_llm(question, OPENAI_API_KEY)
        
        print(f"\n✨ Answer:\n{answer}")
        
        another = input("\n\nAsk another question? (y/n): ").strip().lower()
        if another != 'y':
            break


def sparql_entity_relations_interface(querier: KnowledgeGraphQuerier, entities: list):
    """Interactive interface for finding entity relations"""
    print("\n" + "-"*70)
    print("ENTITY RELATIONS FINDER")
    print("-"*70)
    print("Find all relationships for a specific entity in the graph.")
    
    if not entities:
        print("❌ No entities found in the knowledge graph.")
        input("\nPress Enter to continue...")
        return
    
    while True:
        print("\n📋 Available Entities:")
        
        # Show first 20 entities
        display_entities = entities[:20]
        for i, entity in enumerate(display_entities, 1):
            print(f"  {i}. {entity['text']} ({entity['type']})")
        
        if len(entities) > 20:
            print(f"  ... and {len(entities) - 20} more")
        
        print("\nOptions:")
        print("  - Enter entity name directly")
        print("  - Enter number to select from list")
        print("  - Type 'back' to return")
        
        user_input = input("\nSelect entity: ").strip()
        
        if user_input.lower() in ['back', 'exit', 'quit']:
            break
        
        if not user_input:
            print("❌ Please enter an entity name or number.")
            continue
        
        # Check if input is a number
        if user_input.isdigit():
            idx = int(user_input) - 1
            if 0 <= idx < len(display_entities):
                entity_name = display_entities[idx]['text']
            else:
                print("❌ Invalid number. Please try again.")
                continue
        else:
            entity_name = user_input
        
        print(f"\n🔍 Finding relations for: {entity_name}")
        
        results = querier.find_entity_relations(entity_name)
        
        if not results:
            print(f"❌ No relations found for '{entity_name}'")
            print("   This entity might not exist in the graph or has no connections.")
            continue
        
        print(f"\n📊 Found {len(results)} relations:")
        
        for i, result in enumerate(results, 1):
            predicate = result.get('predicate', 'N/A')
            obj = result.get('object', 'N/A')
            obj_label = result.get('objLabel', 'None')
            
            # Extract readable predicate name
            if '/' in predicate:
                predicate_name = predicate.split('/')[-1].replace('_', ' ')
            elif '#' in predicate:
                predicate_name = predicate.split('#')[-1].replace('_', ' ')
            else:
                predicate_name = predicate
            
            # Extract readable object name
            if obj_label and obj_label != 'None':
                object_name = obj_label
            elif '/' in obj:
                object_name = obj.split('/')[-1].replace('_', ' ')
            elif '#' in obj:
                object_name = obj.split('#')[-1].replace('_', ' ')
            else:
                object_name = obj
            
            print(f"  {i}. {entity_name} → {predicate_name} → {object_name}")
        
        another = input("\n\nLookup another entity? (y/n): ").strip().lower()
        if another != 'y':
            break


def custom_sparql_interface(querier: KnowledgeGraphQuerier, kg_builder: KnowledgeGraphBuilder):
    """Interactive interface for custom SPARQL queries"""
    print("\n" + "-"*70)
    print("CUSTOM SPARQL QUERY")
    print("-"*70)
    print("Execute custom SPARQL queries on the knowledge graph.")
    
    example_query = f"""PREFIX kg: <{kg_builder.ns}>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?subject ?predicate ?object
WHERE {{
    ?subject ?predicate ?object .
}}
LIMIT 10"""
    
    print("\nExample Query:")
    print(example_query)
    
    while True:
        print("\n" + "-"*70)
        print("Enter your SPARQL query (type 'END' on a new line when done):")
        print("Type 'example' to use the example query above")
        print("Type 'back' to return to menu")
        
        user_input = input("\n> ").strip()
        
        if user_input.lower() in ['back', 'exit', 'quit']:
            break
        
        if user_input.lower() == 'example':
            sparql_query = example_query
        else:
            # Multi-line input
            query_lines = [user_input]
            while True:
                line = input()
                if line.strip().upper() == 'END':
                    break
                query_lines.append(line)
            sparql_query = '\n'.join(query_lines)
        
        if not sparql_query.strip():
            print("❌ Empty query. Please try again.")
            continue
        
        print("\n⏳ Executing query...")
        
        try:
            results = querier.query(sparql_query)
            
            if not results:
                print("✓ Query executed successfully. No results returned.")
                continue
            
            print(f"\n✓ Query returned {len(results)} results:")
            print("\n" + "-"*70)
            
            # Display results
            for i, result in enumerate(results, 1):
                print(f"\nResult {i}:")
                for key, value in result.items():
                    print(f"  {key}: {value}")
            
        except Exception as e:
            print(f"\n❌ Query execution failed: {e}")
            print("Please check your SPARQL syntax and try again.")
        
        another = input("\n\nExecute another query? (y/n): ").strip().lower()
        if another != 'y':
            break


def show_graph_statistics(kg_builder: KnowledgeGraphBuilder):
    """Display knowledge graph statistics"""
    print("\n" + "-"*70)
    print("KNOWLEDGE GRAPH STATISTICS")
    print("-"*70)
    
    stats = kg_builder.get_statistics()
    
    print("\n📊 Graph Overview:")
    for key, value in stats.items():
        formatted_key = key.replace('_', ' ').title()
        print(f"  {formatted_key}: {value}")
    
    input("\nPress Enter to continue...")


def main(input_file=None):
    """Run the complete knowledge graph creation pipeline
    
    Args:
        input_file: Path to input document (PDF, TXT, or DOCX). If None, uses sample.
    """
    
    print("\n" + "="*70)
    print("KNOWLEDGE GRAPH CREATION PIPELINE")
    print("="*70 + "\n")# Generate unique ID for this run
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    unique_id = f"{timestamp}_{uuid.uuid4().hex[:8]}"
    output_dir = f"output/kg_{unique_id}"
    
    print("\n" + "="*70)
    print("KNOWLEDGE GRAPH CREATION PIPELINE")
    print(f"Run ID: {unique_id}")
    print("="*70 + "\n")


    
    # Step 1: Load Document
    print("STEP 1: Document Loading")
    print("-" * 70)
    doc_processor = DocumentProcessor()
    
    if input_file:
        text = doc_processor.load_document(input_file)
    else:
        text = doc_processor.create_sample_document()
    
    # Step 2: Preprocess Text
    print("\nSTEP 2: Text Preprocessing")
    print("-" * 70)
    preprocessor = TextPreprocessor()
    preprocessed = preprocessor.preprocess(text)
    
    # Step 3: Extract Entities
    print("\nSTEP 3: Entity Extraction")
    print("-" * 70)
    entity_extractor = EntityExtractor(openai_api_key=OPENAI_API_KEY)
    spacy_entities = entity_extractor.extract_entities_spacy(preprocessed['cleaned'])
    llm_entities = entity_extractor.extract_entities_llm(preprocessed['cleaned'])
    entities = entity_extractor.merge_entities(spacy_entities, llm_entities)
    
    print(f"\nExtracted {len(entities)} unique entities")
    if entities:
        print(f"Sample entities: {[e['text'] for e in entities[:5]]}")
    
    # Step 4: Extract Relations
    print("\nSTEP 4: Relation Extraction")
    print("-" * 70)
    relation_extractor = RelationExtractor(openai_api_key=OPENAI_API_KEY)
    pattern_relations = relation_extractor.extract_relations_pattern(
        preprocessed['cleaned'], entities
    )
    llm_relations = relation_extractor.extract_relations_llm(
        preprocessed['cleaned'], entities
    )
    relations = relation_extractor.merge_relations(pattern_relations, llm_relations)
    
    print(f"\nExtracted {len(relations)} unique relations")
    if relations:
        print(f"Sample relations:")
        for rel in relations[:3]:
            print(f"  - {rel['subject']} → {rel['predicate']} → {rel['object']}")
    
    # Step 5: Build Knowledge Graph
    print("\nSTEP 5: Knowledge Graph Construction")
    print("-" * 70)
    kg_builder = KnowledgeGraphBuilder()
    kg_builder.build_from_extractions(entities, relations)
    
    # Get statistics
    stats = kg_builder.get_statistics()
    print(f"\nGraph Statistics:")
    for key, value in stats.items():
        print(f"  {key}: {value}")
    
    # Save graph
    os.makedirs(output_dir, exist_ok=True)
    kg_builder.save_rdf(f"{output_dir}/knowledge_graph.ttl")
    
    # Step 6: Visualize Graph
    print("\nSTEP 6: Graph Visualization")
    print("-" * 70)
    visualizer = GraphVisualizer(kg_builder)
    visualizer.visualize(f"{output_dir}/knowledge_graph.png")
    
    print("\n" + "="*70)
    print("KNOWLEDGE GRAPH CONSTRUCTION COMPLETED!")
    print("="*70)
    print(f"\n✓ Output files saved in '{output_dir}/' directory:")
    print(f"  - knowledge_graph.ttl (RDF graph)")
    print(f"  - knowledge_graph.png (visualization)")
    
    # Launch interactive query interface
    print("\n" + "="*70)
    print("LAUNCHING INTERACTIVE QUERY INTERFACE")
    print("="*70)
    
    input("\nPress Enter to start querying the knowledge graph...")
    
    interactive_query_interface(kg_builder, entities)
    
    print("\n" + "="*70)
    print("SESSION COMPLETED!")
    print("="*70 + "\n")


if __name__ == "__main__":
    import sys
    
    # Check if file path provided as command line argument
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
        if not os.path.exists(input_file):
            print(f"Error: File '{input_file}' not found!")
            sys.exit(1)
        main(input_file)
    else:
        # Run with sample document
        print("No input file provided. Using sample document.")
        print("Usage: python main.py <path_to_pdf_or_document>")
        print()
        main()