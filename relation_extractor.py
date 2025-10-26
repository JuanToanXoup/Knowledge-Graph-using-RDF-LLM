"""
Relation Extraction Module
Extract relationships between entities using GPT-4o-mini
"""

import json
import time
from typing import List, Dict, Optional, Set
import spacy
from config import SPACY_MODEL
from openai_helper import OpenAIHelper


class RelationExtractor:
    """Extract relationships between entities with robust LLM calls"""
    
    def __init__(self, openai_api_key: Optional[str] = None):
        self.nlp = spacy.load(SPACY_MODEL)
        self.openai = OpenAIHelper(openai_api_key)
        self.use_llm = bool(openai_api_key)
    
    def extract_relations_pattern(self, text: str, entities: List[Dict]) -> List[Dict]:
        """Extract relations using dependency parsing patterns"""
        doc = self.nlp(text)
        relations = []
        
        entity_texts: Set[str] = {e['text'].lower() for e in entities}
        
        for sent in doc.sents:
            for token in sent:
                if token.dep_ in ('nsubj', 'nsubjpass'):
                    subject = token.text
                    verb = token.head.text
                    
                    for child in token.head.children:
                        if child.dep_ in ('dobj', 'pobj', 'attr'):
                            obj = child.text
                            
                            if subject.lower() in entity_texts and obj.lower() in entity_texts:
                                relations.append({
                                    'subject': subject,
                                    'predicate': verb,
                                    'object': obj,
                                    'confidence': 0.7
                                })
        
        print(f"✓ Extracted {len(relations)} relations using patterns")
        return relations
    
    def extract_relations_llm(self, text: str, entities: List[Dict], max_retries: int = 3) -> List[Dict]:
        """Extract relations using GPT-4o-mini with robust error handling
        
        Args:
            text: Input text to extract relations from
            entities: List of extracted entities
            max_retries: Number of retry attempts on failure
        """
        if not self.use_llm:
            print("⚠ LLM extraction skipped: No API key provided")
            return []
        
        if not entities:
            print("⚠ No entities provided for relation extraction")
            return []
        
        # Split text into chunks if too long (GPT-4o-mini has token limits)
        max_chunk_length = 6000  # Conservative limit for context
        if len(text) > max_chunk_length:
            return self._extract_relations_chunked(text, entities, max_retries)
        
        entity_list = [e['text'] for e in entities]
        
        # Enhanced prompt with clearer instructions and examples
        prompt = f"""You are an expert knowledge graph builder. Extract ALL meaningful relationships between the given entities from the text.

ENTITIES TO FIND RELATIONSHIPS FOR:
{', '.join(entity_list)}

TEXT:
{text}

INSTRUCTIONS:
1. Find relationships between ANY two entities from the list
2. Include various relationship types: worked_at, born_in, studied_at, discovered, developed, invented, founded, married_to, colleague_of, collaborated_with, published, awarded, etc.
3. Extract both explicit and implicit relationships
4. Use clear, consistent predicate names (lowercase, underscored)
5. Return ONLY valid JSON, no explanations

REQUIRED FORMAT (return as valid JSON array):
[
  {{"subject": "Entity1", "predicate": "relationship_type", "object": "Entity2"}},
  {{"subject": "Entity2", "predicate": "another_relationship", "object": "Entity3"}}
]

If no relationships found, return: []

JSON OUTPUT:"""
        
        messages = [
            {
                "role": "system", 
                "content": "You are an expert at knowledge graph construction and relationship extraction. You always return valid JSON arrays."
            },
            {
                "role": "user", 
                "content": prompt
            }
        ]
        
        # Retry logic with exponential backoff
        for attempt in range(max_retries):
            try:
                print(f"  Attempting LLM relation extraction (attempt {attempt + 1}/{max_retries})...")
                
                # Use GPT-4o-mini for faster and cheaper extraction
                response = self.openai.chat_completion(
                    messages, 
                    temperature=0.3,  # Lower temperature for more consistent output
                    model="gpt-4o-mini"
                )
                
                if not response:
                    print(f"  ✗ Attempt {attempt + 1}: Empty response from API")
                    if attempt < max_retries - 1:
                        time.sleep(2 ** attempt)  # Exponential backoff
                        continue
                    return []
                
                # Parse the response
                relations = self._parse_llm_response(response)
                
                if relations is not None:
                    # Validate relations
                    valid_relations = self._validate_relations(relations, entities)
                    print(f"✓ Extracted {len(valid_relations)} relations using LLM (GPT-4o-mini)")
                    return valid_relations
                else:
                    print(f"  ✗ Attempt {attempt + 1}: Failed to parse JSON response")
                    if attempt < max_retries - 1:
                        time.sleep(2 ** attempt)
                        continue
                    
            except Exception as e:
                print(f"  ✗ Attempt {attempt + 1}: LLM extraction error: {e}")
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)
                    continue
        
        print("✗ LLM relation extraction failed after all retries")
        return []
    
    def _extract_relations_chunked(self, text: str, entities: List[Dict], max_retries: int) -> List[Dict]:
        """Extract relations from long text by splitting into chunks"""
        print("  Text too long, splitting into chunks...")
        
        # Split into sentences
        doc = self.nlp(text)
        sentences = [sent.text for sent in doc.sents]
        
        # Group sentences into chunks
        chunks = []
        current_chunk = []
        current_length = 0
        max_chunk_length = 5000
        
        for sent in sentences:
            if current_length + len(sent) > max_chunk_length and current_chunk:
                chunks.append(" ".join(current_chunk))
                current_chunk = [sent]
                current_length = len(sent)
            else:
                current_chunk.append(sent)
                current_length += len(sent)
        
        if current_chunk:
            chunks.append(" ".join(current_chunk))
        
        print(f"  Processing {len(chunks)} chunks...")
        
        all_relations = []
        for i, chunk in enumerate(chunks):
            print(f"  Chunk {i+1}/{len(chunks)}...")
            chunk_relations = self.extract_relations_llm(chunk, entities, max_retries=1)
            all_relations.extend(chunk_relations)
        
        print(f"✓ Extracted {len(all_relations)} total relations from all chunks")
        return all_relations
    
    def _parse_llm_response(self, response: str) -> Optional[List[Dict]]:
        """Parse LLM response and extract JSON"""
        try:
            content = response.strip()
            
            # Remove markdown code blocks
            if "```json" in content:
                content = content.split("```json")[1].split("```")[0]
            elif "```" in content:
                content = content.split("```")[1].split("```")[0]
            
            # Clean up common issues
            content = content.strip()
            
            # Try to parse JSON
            relations = json.loads(content)
            
            # Ensure it's a list
            if not isinstance(relations, list):
                print(f"  ✗ Response is not a list: {type(relations)}")
                return None
            
            return relations
            
        except json.JSONDecodeError as e:
            print(f"  ✗ JSON parsing error: {e}")
            print(f"  Response preview: {response[:200]}...")
            return None
        except Exception as e:
            print(f"  ✗ Unexpected parsing error: {e}")
            return None
    
    def _validate_relations(self, relations: List[Dict], entities: List[Dict]) -> List[Dict]:
        """Validate and clean extracted relations"""
        valid_relations = []
        entity_texts = {e['text'].lower() for e in entities}
        
        for rel in relations:
            # Check required fields
            if not all(key in rel for key in ['subject', 'predicate', 'object']):
                continue
            
            # Check if subject and object are valid
            subject = str(rel['subject']).strip()
            predicate = str(rel['predicate']).strip()
            obj = str(rel['object']).strip()
            
            if not subject or not predicate or not obj:
                continue
            
            # Normalize predicate (lowercase, replace spaces with underscores)
            predicate = predicate.lower().replace(' ', '_')
            
            valid_relations.append({
                'subject': subject,
                'predicate': predicate,
                'object': obj,
                'confidence': 0.9  # Higher confidence for LLM extractions
            })
        
        return valid_relations
    
    def merge_relations(self, pattern_relations: List[Dict], llm_relations: List[Dict]) -> List[Dict]:
        """Merge and deduplicate relations, prioritizing LLM results"""
        relation_map = {}
        
        # Add pattern-based relations first
        for rel in pattern_relations:
            key = (rel['subject'].lower(), rel['predicate'].lower(), rel['object'].lower())
            relation_map[key] = rel
        
        # Override/add with LLM relations (higher quality)
        for rel in llm_relations:
            key = (rel['subject'].lower(), rel['predicate'].lower(), rel['object'].lower())
            relation_map[key] = rel
        
        merged = list(relation_map.values())
        print(f"✓ Merged into {len(merged)} unique relations")
        
        if merged:
            print(f"  Sample relations: {merged[:3]}")
        
        return merged