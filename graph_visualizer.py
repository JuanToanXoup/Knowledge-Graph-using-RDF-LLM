"""
Graph Visualization Module
Visualize the knowledge graph using NetworkX and Matplotlib
"""

from typing import Optional
from rdflib import Literal, RDFS
import networkx as nx
import matplotlib.pyplot as plt
from graph_builder import KnowledgeGraphBuilder
from config import (
    DEFAULT_FIGURE_SIZE, 
    MAX_VISUALIZATION_NODES,
    NODE_SIZE,
    ARROW_SIZE
)


class GraphVisualizer:
    """Visualize the knowledge graph"""
    
    def __init__(self, kg_builder: KnowledgeGraphBuilder):
        self.kg = kg_builder
        
    def create_networkx_graph(self) -> nx.DiGraph:
        """Convert RDF graph to NetworkX for visualization"""
        G = nx.DiGraph()
        
        for subj, pred, obj in self.kg.graph:
            subj_label = self._get_label(subj)
            pred_label = self._get_label(pred)
            obj_label = self._get_label(obj)
            
            G.add_edge(subj_label, obj_label, label=pred_label)
        
        return G
    
    def _get_label(self, uri) -> str:
        """Get readable label from URI"""
        if isinstance(uri, Literal):
            return str(uri)
        
        label = self.kg.graph.value(uri, RDFS.label)
        if label:
            return str(label)
        
        uri_str = str(uri)
        return uri_str.split('/')[-1].split('#')[-1].replace('_', ' ')
    
    def visualize(
        self, 
        filename: str = 'knowledge_graph.png', 
        max_nodes: int = MAX_VISUALIZATION_NODES
    ):
        """Create and save visualization"""
        G = self.create_networkx_graph()
        
        # Limit nodes for readability
        if len(G.nodes()) > max_nodes:
            degrees = dict(G.degree())
            top_nodes = sorted(degrees, key=degrees.get, reverse=True)[:max_nodes]
            G = G.subgraph(top_nodes)
        
        plt.figure(figsize=DEFAULT_FIGURE_SIZE)
        pos = nx.spring_layout(G, k=2, iterations=50)
        
        # Draw nodes
        nx.draw_networkx_nodes(
            G, pos, 
            node_color='lightblue', 
            node_size=NODE_SIZE, 
            alpha=0.9
        )
        
        # Draw edges
        nx.draw_networkx_edges(
            G, pos, 
            edge_color='gray', 
            arrows=True, 
            arrowsize=ARROW_SIZE, 
            alpha=0.6
        )
        
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