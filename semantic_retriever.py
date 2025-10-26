"""
Semantic Retriever Module
Vector-based semantic search over knowledge graph
"""

from typing import List, Dict, Optional
import numpy as np
from rdflib import Literal, RDFS
from sentence_transformers import SentenceTransformer
from graph_builder import KnowledgeGraphBuilder
from openai_helper import OpenAIHelper
from config import SENTENCE_TRANSFORMER_MODEL


class SemanticRetriever:
    """Vector-based semantic search over knowledge graph"""
    
    def __init__(self, kg_builder: KnowledgeGraphBuilder):
        self.kg = kg_builder
        self.model = SentenceTransformer(SENTENCE_TRANSFORMER_MODEL)
        self.embeddings: Optional[np.ndarray] = None
        self.triples: List[Dict] = []
        self.openai = OpenAIHelper()
        
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
        
        query_embedding = self.model.encode([query])[0]
        similarities = np.dot(self.embeddings, query_embedding)
        top_indices = np.argsort(similarities)[-top_k:][::-1]
        
        results = []
        for idx in top_indices:
            result = self.triples[idx].copy()
            result['similarity'] = float(similarities[idx])
            results.append(result)
        
        return results
    
    def answer_question_llm(self, question: str, openai_api_key: str) -> str:
        """Answer questions using retrieved context + LLM"""
        relevant = self.search(question, top_k=10)
        
        context = "\n".join([
            f"- {r['subject']} {r['predicate']} {r['object']}"
            for r in relevant
        ])
        
        openai_helper = OpenAIHelper(openai_api_key)
        
        prompt = f"""Based on the following knowledge graph facts, answer the question.
        
Facts:
{context}

Question: {question}

Answer:"""
        
        messages = [
            {"role": "system", "content": "You are a helpful assistant that answers questions based on knowledge graph facts."},
            {"role": "user", "content": prompt}
        ]
        
        response = openai_helper.chat_completion(messages, temperature=0.7)
        return response if response else "Unable to generate answer"