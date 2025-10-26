"""
Document Processing Module
Handles document creation, loading from various formats
"""

import os
from typing import List
from docx import Document
import PyPDF2


class DocumentProcessor:
    """Handles document creation, loading, and basic preprocessing"""
    
    def __init__(self):
        self.documents = []
    
    def load_document(self, filepath: str) -> str:
        """Auto-detect file type and load document
        
        Args:
            filepath: Path to the document file
            
        Returns:
            Extracted text from the document
        """
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"File not found: {filepath}")
        
        ext = os.path.splitext(filepath)[1].lower()
        
        if ext == '.txt':
            return self.load_from_txt(filepath)
        elif ext == '.pdf':
            return self.load_from_pdf(filepath)
        elif ext in ['.docx', '.doc']:
            return self.load_from_docx(filepath)
        else:
            raise ValueError(f"Unsupported file format: {ext}")
    
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