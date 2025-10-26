"""
Entity Extraction Module
Named Entity Recognition using spaCy and OpenAI
"""

import json
from typing import List, Dict, Optional
import spacy
from config import SPACY_MODEL
from openai_helper import OpenAIHelper


class EntityExtractor:
    """Extract named entities using spaCy and OpenAI"""
    
    def __init__(self, openai_api_key: Optional[str] = None):
        self.nlp = spacy.load(SPACY_MODEL)
        self.openai = OpenAIHelper(openai_api_key)
        self.use_llm = bool(openai_api_key)
    
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
        
        messages = [
            {"role": "system", "content": "You are an expert at named entity recognition. Return only valid JSON."},
            {"role": "user", "content": prompt}
        ]
        
        response = self.openai.chat_completion(messages)
        
        if not response:
            return []
        
        try:
            content = response.strip()
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
        
        for ent in spacy_entities:
            key = ent['text'].lower()
            entity_map[key] = {
                'text': ent['text'],
                'type': ent['label']
            }
        
        for ent in llm_entities:
            key = ent['text'].lower()
            entity_map[key] = {
                'text': ent['text'],
                'type': ent.get('type', 'UNKNOWN')
            }
        
        entities = list(entity_map.values())
        print(f"✓ Merged into {len(entities)} unique entities")
        return entities