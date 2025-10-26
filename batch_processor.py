"""
Batch Processor for Multiple PDFs
Process multiple documents and create a unified knowledge graph
"""

import os
import glob
from typing import List, Dict
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

timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
unique_id = f"{timestamp}_{uuid.uuid4().hex[:8]}"
output_dir = f"output/kg_{unique_id}"

class BatchProcessor:
    """Process multiple documents into a single knowledge graph"""
    
    def __init__(self, openai_api_key: str = None):
        self.doc_processor = DocumentProcessor()
        self.text_preprocessor = TextPreprocessor()
        self.entity_extractor = EntityExtractor(openai_api_key)
        self.relation_extractor = RelationExtractor(openai_api_key)
        self.kg_builder = KnowledgeGraphBuilder()
        
        self.all_entities = []
        self.all_relations = []
        self.processed_files = []
    
    def process_single_document(self, filepath: str) -> Dict:
        """Process a single document and extract entities/relations"""
        print(f"\n{'='*70}")
        print(f"Processing: {os.path.basename(filepath)}")
        print(f"{'='*70}")
        
        # Load document
        try:
            text = self.doc_processor.load_document(filepath)
        except Exception as e:
            print(f"✗ Error loading document: {e}")
            return None
        
        # Preprocess
        preprocessed = self.text_preprocessor.preprocess(text)
        
        # Extract entities
        spacy_entities = self.entity_extractor.extract_entities_spacy(
            preprocessed['cleaned']
        )
        llm_entities = self.entity_extractor.extract_entities_llm(
            preprocessed['cleaned']
        )
        entities = self.entity_extractor.merge_entities(spacy_entities, llm_entities)
        
        # Extract relations
        pattern_relations = self.relation_extractor.extract_relations_pattern(
            preprocessed['cleaned'], entities
        )
        llm_relations = self.relation_extractor.extract_relations_llm(
            preprocessed['cleaned'], entities
        )
        relations = self.relation_extractor.merge_relations(
            pattern_relations, llm_relations
        )
        
        return {
            'filepath': filepath,
            'entities': entities,
            'relations': relations,
            'text_length': len(text)
        }
    
    def process_directory(self, directory: str, pattern: str = "*.pdf") -> List[Dict]:
        """Process all matching files in a directory
        
        Args:
            directory: Path to directory containing documents
            pattern: File pattern to match (e.g., "*.pdf", "*.txt")
        """
        files = glob.glob(os.path.join(directory, pattern))
        
        if not files:
            print(f"No files matching '{pattern}' found in {directory}")
            return []
        
        print(f"\nFound {len(files)} file(s) to process")
        
        results = []
        for filepath in files:
            result = self.process_single_document(filepath)
            if result:
                results.append(result)
                self.all_entities.extend(result['entities'])
                self.all_relations.extend(result['relations'])
                self.processed_files.append(filepath)
        
        return results
    
    def process_file_list(self, file_paths: List[str]) -> List[Dict]:
        """Process a list of specific files"""
        results = []
        for filepath in file_paths:
            result = self.process_single_document(filepath)
            if result:
                results.append(result)
                self.all_entities.extend(result['entities'])
                self.all_relations.extend(result['relations'])
                self.processed_files.append(filepath)
        
        return results
    
    def build_unified_graph(self, output_prefix: str = "unified"):
        """Build a unified knowledge graph from all processed documents"""
        print(f"\n{'='*70}")
        print("Building Unified Knowledge Graph")
        print(f"{'='*70}")
        
        # Deduplicate entities and relations across all documents
        entity_map = {}
        for ent in self.all_entities:
            key = ent['text'].lower()
            if key not in entity_map:
                entity_map[key] = ent
        
        unique_entities = list(entity_map.values())
        
        relation_set = set()
        unique_relations = []
        for rel in self.all_relations:
            key = (rel['subject'].lower(), rel['predicate'].lower(), rel['object'].lower())
            if key not in relation_set:
                relation_set.add(key)
                unique_relations.append(rel)
        
        print(f"✓ Deduplicated to {len(unique_entities)} unique entities")
        print(f"✓ Deduplicated to {len(unique_relations)} unique relations")
        
        # Build graph
        self.kg_builder.build_from_extractions(unique_entities, unique_relations)
        
        # Get statistics
        stats = self.kg_builder.get_statistics()
        print(f"\nUnified Graph Statistics:")
        for key, value in stats.items():
            print(f"  {key}: {value}")
        
        # Save outputs
        os.makedirs(output_dir, exist_ok=True)
        output_file = f"{output_dir}/{output_prefix}_knowledge_graph.ttl"
        viz_file = f"{output_dir}/{output_prefix}_knowledge_graph.png"
        
        self.kg_builder.save_rdf(output_file)
        
        # Visualize
        visualizer = GraphVisualizer(self.kg_builder)
        visualizer.visualize(viz_file)
        
        return self.kg_builder
    
    def get_summary(self) -> Dict:
        """Get processing summary"""
        return {
            'total_files': len(self.processed_files),
            'total_entities': len(self.all_entities),
            'total_relations': len(self.all_relations),
            'files': [os.path.basename(f) for f in self.processed_files]
        }


def main():
    """Example usage of batch processor"""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage:")
        print("  Process directory: python batch_processor.py <directory> [pattern]")
        print("  Process files: python batch_processor.py <file1.pdf> <file2.pdf> ...")
        print("\nExamples:")
        print("  python batch_processor.py ./documents/")
        print("  python batch_processor.py ./documents/ '*.pdf'")
        print("  python batch_processor.py doc1.pdf doc2.pdf doc3.pdf")
        return
    
    processor = BatchProcessor(openai_api_key=OPENAI_API_KEY)
    
    path = sys.argv[1]
    
    if os.path.isdir(path):
        # Process directory
        pattern = sys.argv[2] if len(sys.argv) > 2 else "*.pdf"
        results = processor.process_directory(path, pattern)
    elif os.path.isfile(path):
        # Process individual files
        file_paths = sys.argv[1:]
        results = processor.process_file_list(file_paths)
    else:
        print(f"Error: '{path}' is not a valid file or directory")
        return
    
    if not results:
        print("\nNo documents were successfully processed.")
        return
    
    # Build unified graph
    kg_builder = processor.build_unified_graph()
    
    # Print summary
    print(f"\n{'='*70}")
    print("PROCESSING SUMMARY")
    print(f"{'='*70}")
    summary = processor.get_summary()
    print(f"Total files processed: {summary['total_files']}")
    print(f"Total entities extracted: {summary['total_entities']}")
    print(f"Total relations extracted: {summary['total_relations']}")
    print("\nProcessed files:")
    for filename in summary['files']:
        print(f"  - {filename}")
    
    # Setup semantic search
    print(f"\n{'='*70}")
    print("Setting up Semantic Search")
    print(f"{'='*70}")
    retriever = SemanticRetriever(kg_builder)
    retriever.index_graph()
    
    print("\n✓ Knowledge graph ready for queries!")
    print("\nOutput files saved in 'output/' directory:")
    print("  - unified_knowledge_graph.ttl")
    print("  - unified_knowledge_graph.png")


if __name__ == "__main__":
    main()