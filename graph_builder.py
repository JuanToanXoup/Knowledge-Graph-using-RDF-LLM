"""
Knowledge Graph Builder Module
Build and manage RDF Knowledge Graph
"""

import re
from typing import List, Dict
from rdflib import Graph, Namespace, Literal, URIRef, RDF, RDFS
from rdflib.namespace import FOAF
from config import DEFAULT_NAMESPACE, DEFAULT_RDF_FORMAT


class KnowledgeGraphBuilder:
    """Build RDF Knowledge Graph"""
    
    def __init__(self, namespace: str = DEFAULT_NAMESPACE):
        self.graph = Graph()
        self.ns = Namespace(namespace)
        self.graph.bind("kg", self.ns)
        self.graph.bind("foaf", FOAF)
        
    def add_entity(self, entity: Dict):
        """Add an entity node to the graph"""
        entity_uri = self.ns[self._create_uri(entity['text'])]
        entity_type = self._map_entity_type(entity['type'])
        
        self.graph.add((entity_uri, RDF.type, entity_type))
        self.graph.add((entity_uri, RDFS.label, Literal(entity['text'])))
        
    def add_relation(self, relation: Dict):
        """Add a relation (triple) to the graph"""
        subj_uri = self.ns[self._create_uri(relation['subject'])]
        pred_uri = self.ns[self._create_uri(relation['predicate'])]
        obj_uri = self.ns[self._create_uri(relation['object'])]
        
        self.graph.add((subj_uri, pred_uri, obj_uri))
    
    def _create_uri(self, text: str) -> str:
        """Create a valid URI from text"""
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
        for entity in entities:
            self.add_entity(entity)
        
        for relation in relations:
            self.add_relation(relation)
        
        print(f"✓ Knowledge Graph built with {len(self.graph)} triples")
    
    def save_rdf(self, filename: str, format: str = DEFAULT_RDF_FORMAT):
        """Save graph to RDF file"""
        self.graph.serialize(destination=filename, format=format)
        print(f"✓ Knowledge Graph saved to {filename}")
    
    def load_rdf(self, filename: str, format: str = DEFAULT_RDF_FORMAT):
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