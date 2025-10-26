import os
import re
import json
from typing import List, Dict, Tuple, Set
from collections import defaultdict
import openai
from rdflib import Graph, Namespace, Literal, URIRef, RDF, RDFS
from rdflib.namespace import FOAF, XSD
import spacy
import networkx as nx
import matplotlib.pyplot as plt
from docx import Document
import PyPDF2
from sentence_transformers import SentenceTransformer
import numpy as np
import pickle
from dotenv import load_dotenv

load_dotenv
# ============================================================================
# STEP 1: DOCUMENT CREATION AND LOADING
# ============================================================================

class DocumentProcessor:
    """Handles document creation, loading, and basic preprocessing"""
    
    def __init__(self):
        self.documents = []
    
    def create_sample_document(self) -> str:
        """Create a sample document for demonstration"""
        sample_text = """
        Albert Einstein was a theoretical physicist who developed the theory of relativity.
        He was born in Ulm, Germany on March 14, 1879. Einstein worked at the University 
        of Zurich and later at Princeton University. He received the Nobel Prize in Physics 
        in 1921 for his explanation of the photoelectric effect.
        
        Marie Curie was a Polish physicist and chemist who conducted pioneering research 
        on radioactivity. She was the first woman to win a Nobel Prize and remains the only 
        person to win Nobel Prizes in two different sciences. Marie Curie worked at the 
        University of Paris and discovered the elements polonium and radium.
        
        Isaac Newton was an English mathematician, physicist, and astronomer. He formulated 
        the laws of motion and universal gravitation. Newton studied at Cambridge University 
        and later became a professor there. His work laid the foundation for classical mechanics.
        
        The theory of relativity revolutionized our understanding of space, time, and gravity.
        Einstein published his special theory of relativity in 1905 and the general theory 
        in 1915. This work had profound implications for physics and cosmology.
        """
        
        print("✓ Sample document created")
        return sample_text
    
    def load_from_txt(self, filepath: str) -> str:
        """Load document from text file"""
        with open(filepath, 'r', encoding='utf-8') as f:
            text = f.read()
        print(f"✓ Loaded document from {filepath}")
        return text
    
    def load_from_docx(self, filepath: str) -> str:
        """Load document from DOCX file"""
        doc = Document(filepath)
        text = '\n'.join([paragraph.text for paragraph in doc.paragraphs])
        print(f"✓ Loaded document from {filepath}")
        return text
    
    def load_from_pdf(self, filepath: str) -> str:
        """Load document from PDF file"""
        text = ""
        with open(filepath, 'rb') as f:
            pdf_reader = PyPDF2.PdfReader(f)
            for page in pdf_reader.pages:
                text += page.extract_text()
        print(f"✓ Loaded document from {filepath}")
        return text

# ============================================================================
# STEP 2: TEXT PREPROCESSING
# ============================================================================

class TextPreprocessor:
    """Handles text cleaning and preprocessing"""
    
    def __init__(self):
        self.nlp = spacy.load("en_core_web_sm")
    
    def clean_text(self, text: str) -> str:
        """Clean and normalize text"""
        # Remove extra whitespace
        text = re.sub(r'\s+', ' ', text)
        # Remove special characters but keep periods and commas
        text = re.sub(r'[^\w\s.,;:!?-]', '', text)
        text = text.strip()
        print("✓ Text cleaned")
        return text
    
    def sentence_tokenize(self, text: str) -> List[str]:
        """Split text into sentences"""
        doc = self.nlp(text)
        sentences = [sent.text.strip() for sent in doc.sents]
        print(f"✓ Text split into {len(sentences)} sentences")
        return sentences
    
    def preprocess(self, text: str) -> Dict:
        """Complete preprocessing pipeline"""
        cleaned = self.clean_text(text)
        sentences = self.sentence_tokenize(cleaned)
        
        return {
            'original': text,
            'cleaned': cleaned,
            'sentences': sentences
        }
    

    class EntityExtractor:
        """Extract named entities using spaCy and OpenAI"""
    
    def __init__(self, openai_api_key: str = None):
        self.nlp = spacy.load("en_core_web_sm")
        if openai_api_key:
            openai.api_key = openai_api_key
            self.use_llm = True
        else:
            self.use_llm = False
    
    def extract_entities_spacy(self, text: str) -> List[Dict]:
        """Extract entities using spaCy"""
        doc = self.nlp(text)
        entities = []
        
        for ent in doc.ents:
            entities.append({
                'text': ent.text,
                'label': ent.label_,
                'start': ent.start_char,
                'end': ent.end_char
            })
        
        print(f"✓ Extracted {len(entities)} entities using spaCy")
        return entities
    
    def extract_entities_llm(self, text: str) -> List[Dict]:
        """Extract entities using OpenAI LLM for better accuracy"""
        if not self.use_llm:
            return []
        
        prompt = f"""Extract all named entities from the following text. 
        Return them as a JSON list with format: [{{"text": "entity", "type": "PERSON/ORG/GPE/DATE/WORK_OF_ART/etc"}}]
        
        Text: {text}
        
        Entities:"""
        
        try:
            response = openai.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=[
                    {"role": "system", "content": "You are an expert at named entity recognition. Return only valid JSON."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0
            )
            
            content = response.choices[0].message.content.strip()
            # Extract JSON from potential markdown code blocks
            if "```" in content:
                content = content.split("```")[1]
                if content.startswith("json"):
                    content = content[4:]
            
            entities = json.loads(content)
            print(f"✓ Extracted {len(entities)} entities using LLM")
            return entities
        except Exception as e:
            print(f"✗ LLM entity extraction failed: {e}")
            return []
    
    def merge_entities(self, spacy_entities: List[Dict], llm_entities: List[Dict]) -> List[Dict]:
        """Merge and deduplicate entities from different sources"""
        entity_map = {}
        
        # Add spaCy entities
        for ent in spacy_entities:
            key = ent['text'].lower()
            entity_map[key] = {
                'text': ent['text'],
                'type': ent['label']
            }
        
        # Add/update with LLM entities (prioritize LLM types)
        for ent in llm_entities:
            key = ent['text'].lower()
            entity_map[key] = {
                'text': ent['text'],
                'type': ent.get('type', 'UNKNOWN')
            }
        
        entities = list(entity_map.values())
        print(f"✓ Merged into {len(entities)} unique entities")
        return entities

# ============================================================================
# STEP 3: NAMED ENTITY RECOGNITION (NER)
# ============================================================================

class EntityExtractor:
    """Extract named entities using spaCy and OpenAI"""
    
    def __init__(self, openai_api_key: str = None):
        self.nlp = spacy.load("en_core_web_sm")
        if openai_api_key:
            openai.api_key = openai_api_key
            self.use_llm = True
        else:
            self.use_llm = False
    
    def extract_entities_spacy(self, text: str) -> List[Dict]:
        """Extract entities using spaCy"""
        doc = self.nlp(text)
        entities = []
        
        for ent in doc.ents:
            entities.append({
                'text': ent.text,
                'label': ent.label_,
                'start': ent.start_char,
                'end': ent.end_char
            })
        
        print(f"✓ Extracted {len(entities)} entities using spaCy")
        return entities
    
    def extract_entities_llm(self, text: str) -> List[Dict]:
        """Extract entities using OpenAI LLM for better accuracy"""
        if not self.use_llm:
            return []
        
        prompt = f"""Extract all named entities from the following text. 
        Return them as a JSON list with format: [{{"text": "entity", "type": "PERSON/ORG/GPE/DATE/WORK_OF_ART/etc"}}]
        
        Text: {text}
        
        Entities:"""
        
        try:
            response = openai.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=[
                    {"role": "system", "content": "You are an expert at named entity recognition. Return only valid JSON."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0
            )
            
            content = response.choices[0].message.content.strip()
            # Extract JSON from potential markdown code blocks
            if "```" in content:
                content = content.split("```")[1]
                if content.startswith("json"):
                    content = content[4:]
            
            entities = json.loads(content)
            print(f"✓ Extracted {len(entities)} entities using LLM")
            return entities
        except Exception as e:
            print(f"✗ LLM entity extraction failed: {e}")
            return []
    
    def merge_entities(self, spacy_entities: List[Dict], llm_entities: List[Dict]) -> List[Dict]:
        """Merge and deduplicate entities from different sources"""
        entity_map = {}
        
        # Add spaCy entities
        for ent in spacy_entities:
            key = ent['text'].lower()
            entity_map[key] = {
                'text': ent['text'],
                'type': ent['label']
            }
        
        # Add/update with LLM entities (prioritize LLM types)
        for ent in llm_entities:
            key = ent['text'].lower()
            entity_map[key] = {
                'text': ent['text'],
                'type': ent.get('type', 'UNKNOWN')
            }
        
        entities = list(entity_map.values())
        print(f"✓ Merged into {len(entities)} unique entities")
        return entities

# ============================================================================
# STEP 4: RELATION EXTRACTION
# ============================================================================

class RelationExtractor:
    """Extract relationships between entities"""
    
    def __init__(self, openai_api_key: str = None):
        self.nlp = spacy.load("en_core_web_sm")
        if openai_api_key:
            openai.api_key = openai_api_key
            self.use_llm = True
        else:
            self.use_llm = False
    
    def extract_relations_pattern(self, text: str, entities: List[Dict]) -> List[Dict]:
        """Extract relations using dependency parsing patterns"""
        doc = self.nlp(text)
        relations = []
        
        # Create entity lookup
        entity_texts = {e['text'].lower() for e in entities}
        
        # Simple pattern: subject-verb-object
        for sent in doc.sents:
            for token in sent:
                if token.dep_ in ('nsubj', 'nsubjpass'):
                    subject = token.text
                    verb = token.head.text
                    
                    # Find object
                    for child in token.head.children:
                        if child.dep_ in ('dobj', 'pobj', 'attr'):
                            obj = child.text
                            
                            # Check if both are entities
                            if subject.lower() in entity_texts and obj.lower() in entity_texts:
                                relations.append({
                                    'subject': subject,
                                    'predicate': verb,
                                    'object': obj,
                                    'confidence': 0.7
                                })
        
        print(f"✓ Extracted {len(relations)} relations using patterns")
        return relations
    
    def extract_relations_llm(self, text: str, entities: List[Dict]) -> List[Dict]:
        """Extract relations using LLM for better accuracy"""
        if not self.use_llm:
            return []
        
        entity_list = [e['text'] for e in entities]
        
        prompt = f"""Given the following text and entities, extract all meaningful relationships.
        Return as JSON list: [{{"subject": "entity1", "predicate": "relationship", "object": "entity2"}}]
        
        Text: {text}
        
        Entities: {', '.join(entity_list)}
        
        Focus on relationships like: worked_at, born_in, discovered, developed, studied_at, won, etc.
        
        Relations:"""
        
        try:
            response = openai.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=[
                    {"role": "system", "content": "You are an expert at extracting relationships from text. Return only valid JSON."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0
            )
            
            content = response.choices[0].message.content.strip()
            if "```" in content:
                content = content.split("```")[1]
                if content.startswith("json"):
                    content = content[4:]
            
            relations = json.loads(content)
            print(f"✓ Extracted {len(relations)} relations using LLM")
            return relations
        except Exception as e:
            print(f"✗ LLM relation extraction failed: {e}")
            return []
    
    def merge_relations(self, pattern_relations: List[Dict], llm_relations: List[Dict]) -> List[Dict]:
        """Merge and deduplicate relations"""
        relation_set = set()
        merged = []
        
        for rel in pattern_relations + llm_relations:
            key = (rel['subject'].lower(), rel['predicate'].lower(), rel['object'].lower())
            if key not in relation_set:
                relation_set.add(key)
                merged.append(rel)
        
        print(f"✓ Merged into {len(merged)} unique relations")
        return merged
    
# ============================================================================
# STEP 5: KNOWLEDGE GRAPH CONSTRUCTION (RDF)
# ============================================================================

class KnowledgeGraphBuilder:
    """Build RDF Knowledge Graph"""
    
    def __init__(self, namespace: str = "http://example.org/kg/"):
        self.graph = Graph()
        self.ns = Namespace(namespace)
        self.graph.bind("kg", self.ns)
        self.graph.bind("foaf", FOAF)
        
    def add_entity(self, entity: Dict):
        """Add an entity node to the graph"""
        entity_uri = self.ns[self._create_uri(entity['text'])]
        entity_type = self._map_entity_type(entity['type'])
        
        # Add type
        self.graph.add((entity_uri, RDF.type, entity_type))
        # Add label
        self.graph.add((entity_uri, RDFS.label, Literal(entity['text'])))
        
    def add_relation(self, relation: Dict):
        """Add a relation (triple) to the graph"""
        subj_uri = self.ns[self._create_uri(relation['subject'])]
        pred_uri = self.ns[self._create_uri(relation['predicate'])]
        obj_uri = self.ns[self._create_uri(relation['object'])]
        
        self.graph.add((subj_uri, pred_uri, obj_uri))
    
    def _create_uri(self, text: str) -> str:
        """Create a valid URI from text"""
        # Remove special characters and replace spaces with underscores
        uri = re.sub(r'[^\w\s-]', '', text)
        uri = re.sub(r'[\s-]+', '_', uri)
        return uri
    
    def _map_entity_type(self, entity_type: str) -> URIRef:
        """Map entity type to RDF class"""
        type_mapping = {
            'PERSON': FOAF.Person,
            'ORG': FOAF.Organization,
            'GPE': self.ns.Place,
            'DATE': self.ns.Date,
            'WORK_OF_ART': self.ns.CreativeWork,
            'EVENT': self.ns.Event,
        }
        return type_mapping.get(entity_type, self.ns.Entity)
    
    def build_from_extractions(self, entities: List[Dict], relations: List[Dict]):
        """Build complete knowledge graph"""
        # Add all entities
        for entity in entities:
            self.add_entity(entity)
        
        # Add all relations
        for relation in relations:
            self.add_relation(relation)
        
        print(f"✓ Knowledge Graph built with {len(self.graph)} triples")
    
    def save_rdf(self, filename: str, format: str = 'turtle'):
        """Save graph to RDF file"""
        self.graph.serialize(destination=filename, format=format)
        print(f"✓ Knowledge Graph saved to {filename}")
    
    def load_rdf(self, filename: str, format: str = 'turtle'):
        """Load graph from RDF file"""
        self.graph.parse(filename, format=format)
        print(f"✓ Knowledge Graph loaded from {filename}")
    
    def get_statistics(self) -> Dict:
        """Get graph statistics"""
        stats = {
            'total_triples': len(self.graph),
            'unique_subjects': len(set(self.graph.subjects())),
            'unique_predicates': len(set(self.graph.predicates())),
            'unique_objects': len(set(self.graph.objects()))
        }
        return stats
    

    # ============================================================================
# STEP 6: GRAPH VISUALIZATION
# ============================================================================

class GraphVisualizer:
    """Visualize the knowledge graph"""
    
    def __init__(self, kg_builder: KnowledgeGraphBuilder):
        self.kg = kg_builder
        
    def create_networkx_graph(self) -> nx.DiGraph:
        """Convert RDF graph to NetworkX for visualization"""
        G = nx.DiGraph()
        
        for subj, pred, obj in self.kg.graph:
            # Get readable labels
            subj_label = self._get_label(subj)
            pred_label = self._get_label(pred)
            obj_label = self._get_label(obj)
            
            G.add_edge(subj_label, obj_label, label=pred_label)
        
        return G
    
    def _get_label(self, uri) -> str:
        """Get readable label from URI"""
        if isinstance(uri, Literal):
            return str(uri)
        
        # Try to get RDFS label
        label = self.kg.graph.value(uri, RDFS.label)
        if label:
            return str(label)
        
        # Fall back to last part of URI
        uri_str = str(uri)
        return uri_str.split('/')[-1].split('#')[-1].replace('_', ' ')
    
    def visualize(self, filename: str = 'knowledge_graph.png', max_nodes: int = 50):
        """Create and save visualization"""
        G = self.create_networkx_graph()
        
        # Limit nodes for readability
        if len(G.nodes()) > max_nodes:
            # Keep most connected nodes
            degrees = dict(G.degree())
            top_nodes = sorted(degrees, key=degrees.get, reverse=True)[:max_nodes]
            G = G.subgraph(top_nodes)
        
        plt.figure(figsize=(16, 12))
        pos = nx.spring_layout(G, k=2, iterations=50)
        
        # Draw nodes
        nx.draw_networkx_nodes(G, pos, node_color='lightblue', 
                              node_size=3000, alpha=0.9)
        
        # Draw edges
        nx.draw_networkx_edges(G, pos, edge_color='gray', 
                              arrows=True, arrowsize=20, alpha=0.6)
        
        # Draw labels
        nx.draw_networkx_labels(G, pos, font_size=8, font_weight='bold')
        
        # Draw edge labels
        edge_labels = nx.get_edge_attributes(G, 'label')
        nx.draw_networkx_edge_labels(G, pos, edge_labels, font_size=6)
        
        plt.axis('off')
        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        print(f"✓ Graph visualization saved to {filename}")
        plt.close()

# ============================================================================
# STEP 7: SPARQL QUERY INTERFACE
# ============================================================================

class KnowledgeGraphQuerier:
    """Query the knowledge graph using SPARQL"""
    
    def __init__(self, kg_builder: KnowledgeGraphBuilder):
        self.kg = kg_builder
    
    def query(self, sparql_query: str) -> List[Dict]:
        """Execute SPARQL query"""
        results = []
        qres = self.kg.graph.query(sparql_query)
        
        for row in qres:
            result_dict = {}
            for var in qres.vars:
                value = row[var]
                if isinstance(value, Literal):
                    result_dict[str(var)] = str(value)
                else:
                    # Get label if available
                    label = self.kg.graph.value(value, RDFS.label)
                    result_dict[str(var)] = str(label) if label else str(value)
            results.append(result_dict)
        
        return results
    
    def find_entity_relations(self, entity_name: str) -> List[Dict]:
        """Find all relations for an entity"""
        query = f"""
        PREFIX kg: <{self.kg.ns}>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT ?predicate ?object ?objLabel
        WHERE {{
            ?subject rdfs:label "{entity_name}" .
            ?subject ?predicate ?object .
            OPTIONAL {{ ?object rdfs:label ?objLabel }}
        }}
        """
        return self.query(query)
    
    def find_path(self, entity1: str, entity2: str) -> List[Dict]:
        """Find connection path between two entities"""
        query = f"""
        PREFIX kg: <{self.kg.ns}>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT ?relation ?intermediate
        WHERE {{
            ?e1 rdfs:label "{entity1}" .
            ?e2 rdfs:label "{entity2}" .
            ?e1 ?relation ?intermediate .
            ?intermediate ?rel2 ?e2 .
        }}
        LIMIT 10
        """
        return self.query(query)
# ============================================================================
# STEP 8: SEMANTIC SEARCH AND RETRIEVAL
# ============================================================================

class SemanticRetriever:
    """Vector-based semantic search over knowledge graph"""
    
    def __init__(self, kg_builder: KnowledgeGraphBuilder):
        self.kg = kg_builder
        self.model = SentenceTransformer('all-MiniLM-L6-v2')
        self.embeddings = None
        self.triples = []
        
    def index_graph(self):
        """Create embeddings for all triples"""
        self.triples = []
        texts = []
        
        for subj, pred, obj in self.kg.graph:
            subj_label = self._get_label(subj)
            pred_label = self._get_label(pred)
            obj_label = self._get_label(obj)
            
            triple_text = f"{subj_label} {pred_label} {obj_label}"
            texts.append(triple_text)
            self.triples.append({
                'subject': subj_label,
                'predicate': pred_label,
                'object': obj_label,
                'text': triple_text
            })
        
        # Create embeddings
        self.embeddings = self.model.encode(texts, show_progress_bar=False)
        print(f"✓ Indexed {len(self.triples)} triples for semantic search")
    
    def _get_label(self, uri) -> str:
        """Get readable label"""
        if isinstance(uri, Literal):
            return str(uri)
        
        label = self.kg.graph.value(uri, RDFS.label)
        if label:
            return str(label)
        
        uri_str = str(uri)
        return uri_str.split('/')[-1].split('#')[-1].replace('_', ' ')
    
    def search(self, query: str, top_k: int = 5) -> List[Dict]:
        """Semantic search for relevant triples"""
        if self.embeddings is None:
            self.index_graph()
        
        # Encode query
        query_embedding = self.model.encode([query])[0]
        
        # Calculate similarities
        similarities = np.dot(self.embeddings, query_embedding)
        
        # Get top k
        top_indices = np.argsort(similarities)[-top_k:][::-1]
        
        results = []
        for idx in top_indices:
            result = self.triples[idx].copy()
            result['similarity'] = float(similarities[idx])
            results.append(result)
        
        return results
    
    def answer_question_llm(self, question: str, openai_api_key: str) -> str:
        """Answer questions using retrieved context + LLM"""
        # Retrieve relevant triples
        relevant = self.search(question, top_k=10)
        
        # Build context
        context = "\n".join([
            f"- {r['subject']} {r['predicate']} {r['object']}"
            for r in relevant
        ])
        
        # Query LLM
        openai.api_key = openai_api_key
        
        prompt = f"""Based on the following knowledge graph facts, answer the question.
        
Facts:
{context}

Question: {question}

Answer:"""
        
        try:
            response = openai.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=[
                    {"role": "system", "content": "You are a helpful assistant that answers questions based on knowledge graph facts."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.7
            )
            
            answer = response.choices[0].message.content
            return answer
        except Exception as e:
            return f"Error generating answer: {e}"
        

# ============================================================================
# MAIN PIPELINE
# ============================================================================

class KnowledgeGraphPipeline:
    """Complete end-to-end pipeline"""
    
    def __init__(self, openai_api_key: str = None):
        self.api_key = openai_api_key
        self.doc_processor = DocumentProcessor()
        self.preprocessor = TextPreprocessor()
        self.entity_extractor = EntityExtractor(openai_api_key)
        self.relation_extractor = RelationExtractor(openai_api_key)
        self.kg_builder = None
        self.querier = None
        self.retriever = None
        self.visualizer = None
        
        self.text = None
        self.entities = None
        self.relations = None
    
    def run(self, input_source: str = "sample", file_path: str = None):
        """Execute complete pipeline"""
        print("\n" + "="*70)
        print("KNOWLEDGE GRAPH CREATION PIPELINE")
        print("="*70)
        
        # Step 1: Load Document
        print("\n[STEP 1] DOCUMENT LOADING")
        print("-"*70)
        if input_source == "sample":
            self.text = self.doc_processor.create_sample_document()
        elif input_source == "txt":
            self.text = self.doc_processor.load_from_txt(file_path)
        elif input_source == "docx":
            self.text = self.doc_processor.load_from_docx(file_path)
        elif input_source == "pdf":
            self.text = self.doc_processor.load_from_pdf(file_path)
        
        # Step 2: Preprocess
        print("\n[STEP 2] TEXT PREPROCESSING")
        print("-"*70)
        processed = self.preprocessor.preprocess(self.text)
        print(f"Sample sentence: {processed['sentences'][0][:100]}...")
        
        # Step 3: Extract Entities
        print("\n[STEP 3] NAMED ENTITY RECOGNITION")
        print("-"*70)
        spacy_entities = self.entity_extractor.extract_entities_spacy(processed['cleaned'])
        llm_entities = self.entity_extractor.extract_entities_llm(processed['cleaned'])
        self.entities = self.entity_extractor.merge_entities(spacy_entities, llm_entities)
        
        print(f"\nSample entities:")
        for ent in self.entities[:5]:
            print(f"  - {ent['text']} ({ent['type']})")
        
        # Step 4: Extract Relations
        print("\n[STEP 4] RELATION EXTRACTION")
        print("-"*70)
        pattern_relations = self.relation_extractor.extract_relations_pattern(
            processed['cleaned'], self.entities
        )
        llm_relations = self.relation_extractor.extract_relations_llm(
            processed['cleaned'], self.entities
        )
        self.relations = self.relation_extractor.merge_relations(
            pattern_relations, llm_relations
        )
        
        print(f"\nSample relations:")
        for rel in self.relations[:5]:
            print(f"  - {rel['subject']} → {rel['predicate']} → {rel['object']}")
        
        # Step 5: Build Knowledge Graph
        print("\n[STEP 5] KNOWLEDGE GRAPH CONSTRUCTION (RDF)")
        print("-"*70)
        self.kg_builder = KnowledgeGraphBuilder()
        self.kg_builder.build_from_extractions(self.entities, self.relations)
        
        stats = self.kg_builder.get_statistics()
        print(f"\nGraph Statistics:")
        for key, value in stats.items():
            print(f"  - {key}: {value}")
        
        # Save RDF
        self.kg_builder.save_rdf("knowledge_graph.ttl", format="turtle")
        self.kg_builder.save_rdf("knowledge_graph.xml", format="xml")
        
        # Step 6: Visualize
        print("\n[STEP 6] GRAPH VISUALIZATION")
        print("-"*70)
        self.visualizer = GraphVisualizer(self.kg_builder)
        self.visualizer.visualize("knowledge_graph.png")
        
        # Step 7: Setup Querying
        print("\n[STEP 7] SPARQL QUERY SETUP")
        print("-"*70)
        self.querier = KnowledgeGraphQuerier(self.kg_builder)
        print("✓ SPARQL query interface ready")
        
        # Step 8: Setup Retrieval
        print("\n[STEP 8] SEMANTIC SEARCH SETUP")
        print("-"*70)
        self.retriever = SemanticRetriever(self.kg_builder)
        self.retriever.index_graph()
        
        print("\n" + "="*70)
        print("PIPELINE COMPLETE!")
        print("="*70)
        
        return self
    
    def demo_queries(self):
        """Demonstrate query capabilities"""
        print("\n" + "="*70)
        print("DEMONSTRATION: QUERYING THE KNOWLEDGE GRAPH")
        print("="*70)
        
        if not self.querier or not self.retriever:
            print("Please run the pipeline first!")
            return
        
        # SPARQL Query Demo
        print("\n[SPARQL QUERY] Find all relations for 'Albert Einstein':")
        print("-"*70)
        results = self.querier.find_entity_relations("Albert Einstein")
        for r in results[:5]:
            print(f"  {r}")
        
        # Semantic Search Demo
        print("\n[SEMANTIC SEARCH] Query: 'Who won a Nobel Prize?'")
        print("-"*70)
        results = self.retriever.search("Who won a Nobel Prize?", top_k=3)
        for r in results:
            print(f"  [{r['similarity']:.3f}] {r['text']}")
        
        # LLM-powered Q&A Demo
        if self.api_key:
            print("\n[LLM Q&A] Question: 'What did Einstein develop?'")
            print("-"*70)
            answer = self.retriever.answer_question_llm(
                "What did Einstein develop?", 
                self.api_key
            )
            print(f"  Answer: {answer}")
    
    def save_pipeline(self, filename: str = "kg_pipeline.pkl"):
        """Save pipeline state"""
        state = {
            'text': self.text,
            'entities': self.entities,
            'relations': self.relations,
        }
        with open(filename, 'wb') as f:
            pickle.dump(state, f)
        print(f"✓ Pipeline state saved to {filename}")
    
    def interactive_mode(self):
        """Interactive query mode"""
        print("\n" + "="*70)
        print("INTERACTIVE QUERY MODE")
        print("="*70)
        print("Commands:")
        print("  search <query>  - Semantic search")
        print("  sparql <entity> - Find relations for entity")
        print("  ask <question>  - Ask question (requires OpenAI key)")
        print("  stats           - Show graph statistics")
        print("  quit            - Exit")
        print("-"*70)
        
        while True:
            try:
                cmd = input("\n> ").strip()
                
                if cmd == "quit":
                    break
                elif cmd == "stats":
                    stats = self.kg_builder.get_statistics()
                    for key, value in stats.items():
                        print(f"  {key}: {value}")
                elif cmd.startswith("search "):
                    query = cmd[7:]
                    results = self.retriever.search(query, top_k=5)
                    for r in results:
                        print(f"  [{r['similarity']:.3f}] {r['text']}")
                elif cmd.startswith("sparql "):
                    entity = cmd[7:]
                    results = self.querier.find_entity_relations(entity)
                    for r in results[:10]:
                        print(f"  {r}")
                elif cmd.startswith("ask "):
                    if not self.api_key:
                        print("  OpenAI API key required for Q&A")
                        continue
                    question = cmd[4:]
                    answer = self.retriever.answer_question_llm(question, self.api_key)
                    print(f"  {answer}")
                else:
                    print("  Unknown command")
            except KeyboardInterrupt:
                break
            except Exception as e:
                print(f"  Error: {e}")

                # ============================================================================
# USAGE EXAMPLES
# ============================================================================

if __name__ == "__main__":
    """
    Example usage of the Knowledge Graph pipeline
    """
    
    print("""
    ╔══════════════════════════════════════════════════════════════════════╗
    ║        KNOWLEDGE GRAPH CREATION - END-TO-END TUTORIAL               ║
    ╚══════════════════════════════════════════════════════════════════════╝
    
    This script demonstrates the complete process of creating a Knowledge
    Graph from text documents, including:
    
    1. Document Loading (TXT, DOCX, PDF)
    2. Text Preprocessing
    3. Named Entity Recognition (NER)
    4. Relation Extraction
    5. RDF Knowledge Graph Construction
    6. Graph Visualization
    7. SPARQL Querying
    8. Semantic Search & Retrieval
    """)
    
    # Set your OpenAI API key here
    OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", None)
    
    if not OPENAI_API_KEY:
        print("\n⚠️  WARNING: No OpenAI API key found!")
        print("   Set OPENAI_API_KEY environment variable for enhanced extraction.")
        print("   The pipeline will use spaCy-only mode (less accurate).\n")
        response = input("Continue without OpenAI? (y/n): ")
        if response.lower() != 'y':
            exit()
    
    # ========================================================================
    # EXAMPLE 1: Run complete pipeline with sample document
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 1: Creating Knowledge Graph from Sample Document")
    print("="*70)
    
    # Create pipeline
    pipeline = KnowledgeGraphPipeline(openai_api_key=OPENAI_API_KEY)
    
    # Run complete pipeline
    pipeline.run(input_source="sample")
    
    # Demonstrate queries
    pipeline.demo_queries()
    
    # ========================================================================
    # EXAMPLE 2: Load from file (uncomment to use)
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 2: Loading Knowledge Graph from Different File Types")
    print("="*70)
    print("""
    To load from a file, use:
    
    # From text file:
    pipeline.run(input_source="txt", file_path="your_document.txt")
    
    # From DOCX file:
    pipeline.run(input_source="docx", file_path="your_document.docx")
    
    # From PDF file:
    pipeline.run(input_source="pdf", file_path="your_document.pdf")
    """)
    
    # ========================================================================
    # EXAMPLE 3: Custom SPARQL queries
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 3: Custom SPARQL Queries")
    print("="*70)
    
    # Find all people
    custom_query = """
    PREFIX foaf: <http://xmlns.com/foaf/0.1/>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    
    SELECT ?person ?label
    WHERE {
        ?person a foaf:Person .
        ?person rdfs:label ?label .
    }
    """
    
    print("\nQuery: Find all persons in the graph")
    print("-"*70)
    results = pipeline.querier.query(custom_query)
    for r in results:
        print(f"  {r}")
    
    # ========================================================================
    # EXAMPLE 4: Semantic search examples
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 4: Semantic Search Examples")
    print("="*70)
    
    queries = [
        "universities and education",
        "scientific discoveries",
        "awards and prizes"
    ]
    
    for q in queries:
        print(f"\nQuery: '{q}'")
        print("-"*70)
        results = pipeline.retriever.search(q, top_k=3)
        for r in results:
            print(f"  [{r['similarity']:.3f}] {r['text']}")
    
    # ========================================================================
    # EXAMPLE 5: Save and load pipeline
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 5: Saving Pipeline State")
    print("="*70)
    
    pipeline.save_pipeline("my_kg_pipeline.pkl")
    
    # ========================================================================
    # EXAMPLE 6: Accessing the RDF graph directly
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 6: Direct RDF Graph Access")
    print("="*70)
    
    print("\nRDF Serialization Formats Available:")
    print("-"*70)
    print("  - Turtle (.ttl): Human-readable")
    print("  - XML (.xml): Standard interchange format")
    print("  - N-Triples (.nt): Simple line-based format")
    print("  - JSON-LD (.jsonld): JSON format")
    
    print("\nSample RDF triple in Turtle format:")
    print("-"*70)
    # Show first few triples
    for i, (s, p, o) in enumerate(pipeline.kg_builder.graph):
        if i >= 3:
            break
        print(f"  {s} {p} {o}")
    
    # ========================================================================
    # EXAMPLE 7: Building KG from custom text
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 7: Custom Text Processing")
    print("="*70)
    
    custom_text = """
    Apple Inc. was founded by Steve Jobs, Steve Wozniak, and Ronald Wayne.
    The company is headquartered in Cupertino, California. Tim Cook is the 
    current CEO of Apple. Apple develops products like the iPhone and MacBook.
    """
    
    print("Creating KG from custom text...")
    print("-"*70)
    
    # Create new pipeline for custom text
    custom_pipeline = KnowledgeGraphPipeline(openai_api_key=OPENAI_API_KEY)
    
    # Process custom text
    custom_pipeline.text = custom_text
    processed = custom_pipeline.preprocessor.preprocess(custom_text)
    
    # Extract entities
    spacy_ents = custom_pipeline.entity_extractor.extract_entities_spacy(processed['cleaned'])
    llm_ents = custom_pipeline.entity_extractor.extract_entities_llm(processed['cleaned'])
    custom_pipeline.entities = custom_pipeline.entity_extractor.merge_entities(spacy_ents, llm_ents)
    
    # Extract relations
    pattern_rels = custom_pipeline.relation_extractor.extract_relations_pattern(
        processed['cleaned'], custom_pipeline.entities
    )
    llm_rels = custom_pipeline.relation_extractor.extract_relations_llm(
        processed['cleaned'], custom_pipeline.entities
    )
    custom_pipeline.relations = custom_pipeline.relation_extractor.merge_relations(
        pattern_rels, llm_rels
    )
    
    # Build KG
    custom_pipeline.kg_builder = KnowledgeGraphBuilder()
    custom_pipeline.kg_builder.build_from_extractions(
        custom_pipeline.entities, 
        custom_pipeline.relations
    )
    
    print(f"\nExtracted {len(custom_pipeline.entities)} entities")
    print(f"Extracted {len(custom_pipeline.relations)} relations")
    print(f"Built KG with {len(custom_pipeline.kg_builder.graph)} triples")
    
    # ========================================================================
    # EXAMPLE 8: Graph Analysis
    # ========================================================================
    
    print("\n" + "="*70)
    print("EXAMPLE 8: Graph Analysis")
    print("="*70)
    
    # Most connected entities
    print("\nMost Connected Entities:")
    print("-"*70)
    
    entity_connections = {}
    for entity in pipeline.entities:
        entity_name = entity['text']
        results = pipeline.querier.find_entity_relations(entity_name)
        entity_connections[entity_name] = len(results)
    
    sorted_entities = sorted(entity_connections.items(), key=lambda x: x[1], reverse=True)
    for entity, count in sorted_entities[:5]:
        print(f"  {entity}: {count} connections")
    
    # ========================================================================
    # INTERACTIVE MODE (optional)
    # ========================================================================
    
    print("\n" + "="*70)
    print("INTERACTIVE MODE")
    print("="*70)
    
    response = input("\nEnter interactive query mode? (y/n): ")
    if response.lower() == 'y':
        pipeline.interactive_mode()
    
    # ========================================================================
    # SUMMARY
    # ========================================================================
    
    print("\n" + "="*70)
    print("TUTORIAL COMPLETE!")
    print("="*70)
    print("""
    Generated Files:
    ├── knowledge_graph.ttl      (RDF in Turtle format)
    ├── knowledge_graph.xml      (RDF in XML format)
    ├── knowledge_graph.png      (Graph visualization)
    └── my_kg_pipeline.pkl       (Saved pipeline state)
    
    Next Steps:
    1. Load your own documents using pipeline.run()
    2. Query the graph using SPARQL or semantic search
    3. Integrate with your applications via the RDF files
    4. Extend with custom entity types and relations
    5. Build RAG systems using the semantic retriever
    
    Key Components to Remember:
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    📄 Document Processing:
       - Supports TXT, DOCX, PDF formats
       - Automatic text cleaning and sentence tokenization
    
    🏷️  Named Entity Recognition:
       - spaCy for fast extraction
       - OpenAI LLM for enhanced accuracy
       - Automatic entity merging and deduplication
    
    🔗 Relation Extraction:
       - Pattern-based using dependency parsing
       - LLM-based for complex relations
       - Merge strategies for best results
    
    🕸️  RDF Knowledge Graph:
       - Standard RDF/RDFS/OWL format
       - Multiple serialization formats
       - FOAF vocabulary for people/orgs
    
    🔍 Querying:
       - SPARQL for structured queries
       - Semantic search with embeddings
       - LLM-powered question answering
    
    📊 Visualization:
       - NetworkX graph layouts
       - Automatic node limiting for readability
       - Export to various image formats
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    For production use, consider:
    - Neo4j or GraphDB for large-scale graphs
    - FAISS or Pinecone for efficient vector search
    - Custom entity/relation schemas
    - Incremental graph updates
    - Graph embeddings (Node2Vec, TransE)
    
    Happy Knowledge Graph Building! 🚀
    """)
    
    print("\n" + "="*70)
    print("Run this script again to process new documents!")
    print("="*70 + "\n")