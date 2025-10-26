"""
Text Preprocessing Module
Handles text cleaning and preprocessing
"""

import re
from typing import List, Dict
import spacy
from config import SPACY_MODEL


class TextPreprocessor:
    """Handles text cleaning and preprocessing"""
    
    def __init__(self):
        self.nlp = spacy.load(SPACY_MODEL)
    
    def clean_text(self, text: str) -> str:
        """Clean and normalize text"""
        text = re.sub(r'\s+', ' ', text)
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