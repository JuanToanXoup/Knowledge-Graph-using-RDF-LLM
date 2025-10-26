"""
OpenAI API Helper Module
Simple wrapper for OpenAI API using requests
"""

import os
import requests
from typing import List, Dict, Optional
from config import OPENAI_API_URL, OPENAI_MODEL, REQUEST_TIMEOUT


class OpenAIHelper:
    """Simple OpenAI API wrapper using requests"""
    
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.getenv("OPENAI_API_KEY")
        self.api_url = OPENAI_API_URL
    
    def chat_completion(
        self, 
        messages: List[Dict], 
        temperature: float = 0, 
        model: str = OPENAI_MODEL
    ) -> Optional[str]:
        """Make a chat completion request"""
        if not self.api_key:
            return None
        
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "model": model,
            "messages": messages,
            "temperature": temperature
        }
        
        try:
            response = requests.post(
                self.api_url, 
                headers=headers, 
                json=payload, 
                timeout=REQUEST_TIMEOUT
            )
            response.raise_for_status()
            return response.json()['choices'][0]['message']['content']
        except Exception as e:
            print(f"OpenAI API Error: {e}")
            return None