"""
Configuration and Constants for Knowledge Graph System
"""

import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# API Configuration
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
OPENAI_MODEL = "gpt-3.5-turbo"

# Knowledge Graph Configuration
DEFAULT_NAMESPACE = "http://example.org/kg/"

# Model Configuration
SPACY_MODEL = "en_core_web_sm"
SENTENCE_TRANSFORMER_MODEL = "all-MiniLM-L6-v2"

# Visualization Configuration
DEFAULT_FIGURE_SIZE = (16, 12)
MAX_VISUALIZATION_NODES = 50
NODE_SIZE = 3000
ARROW_SIZE = 20

# File Paths
DEFAULT_OUTPUT_DIR = "output"
DEFAULT_RDF_FORMAT = "turtle"

# Request Configuration
REQUEST_TIMEOUT = 30