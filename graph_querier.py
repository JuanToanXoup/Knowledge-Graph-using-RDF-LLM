"""
Knowledge Graph Querier Module
Query the knowledge graph using SPARQL
"""

from typing import List, Dict
from rdflib import Literal, RDFS
from graph_builder import KnowledgeGraphBuilder


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