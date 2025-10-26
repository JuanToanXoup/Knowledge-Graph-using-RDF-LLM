"""
Streamlit Frontend for Knowledge Graph System
Clean and functional interface for document processing and graph querying
"""

import streamlit as st
import requests
from PIL import Image
import io
import json
import pandas as pd
from datetime import datetime

# Configuration
API_BASE_URL = "http://localhost:8000"

# Page configuration
st.set_page_config(
    page_title="Knowledge Graph System",
    page_icon="🧠",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Custom CSS for cleaner look
st.markdown("""
    <style>
    .stApp {
        max-width: 100%;
    }
    .upload-section {
        padding: 2rem;
        border-radius: 10px;
        background-color: #f8f9fa;
        margin-bottom: 2rem;
    }
    .metric-card {
        padding: 1rem;
        border-radius: 8px;
        background-color: #ffffff;
        border: 1px solid #e0e0e0;
    }
    </style>
""", unsafe_allow_html=True)


def check_api_health():
    """Check if API is running"""
    try:
        response = requests.get(f"{API_BASE_URL}/health", timeout=2)
        return response.status_code == 200
    except:
        return False


def upload_document(file):
    """Upload document to create knowledge graph"""
    files = {"file": (file.name, file, file.type)}
    try:
        response = requests.post(f"{API_BASE_URL}/upload", files=files)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"Upload failed: {str(e)}")
        return None


def get_graphs():
    """Fetch all active graphs"""
    try:
        response = requests.get(f"{API_BASE_URL}/graphs")
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"Failed to fetch graphs: {str(e)}")
        return None


def get_graph_info(graph_id):
    """Get detailed graph information"""
    try:
        response = requests.get(f"{API_BASE_URL}/graph/{graph_id}")
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"Failed to fetch graph info: {str(e)}")
        return None


def get_visualization(graph_id):
    """Fetch graph visualization"""
    try:
        response = requests.get(f"{API_BASE_URL}/visualization/{graph_id}")
        response.raise_for_status()
        return Image.open(io.BytesIO(response.content))
    except requests.exceptions.RequestException as e:
        st.error(f"Failed to fetch visualization: {str(e)}")
        return None


def semantic_search(graph_id, query, top_k=5):
    """Perform semantic search"""
    try:
        payload = {"graph_id": graph_id, "query": query, "top_k": top_k}
        response = requests.post(f"{API_BASE_URL}/semantic_search", json=payload)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"Search failed: {str(e)}")
        return None


def question_answer(graph_id, question):
    """Ask question using LLM"""
    try:
        payload = {"graph_id": graph_id, "question": question}
        response = requests.post(f"{API_BASE_URL}/question_answer", json=payload)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"Question answering failed: {str(e)}")
        return None


def entity_relations(graph_id, entity_name):
    """Get entity relations"""
    try:
        payload = {"graph_id": graph_id, "entity_name": entity_name}
        response = requests.post(f"{API_BASE_URL}/entity_relations", json=payload)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"Failed to fetch relations: {str(e)}")
        return None


def sparql_query(graph_id, query):
    """Execute SPARQL query"""
    try:
        payload = {"graph_id": graph_id, "query": query}
        response = requests.post(f"{API_BASE_URL}/sparql_query", json=payload)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        st.error(f"SPARQL query failed: {str(e)}")
        return None


def download_graph(graph_id):
    """Download RDF graph file"""
    try:
        response = requests.get(f"{API_BASE_URL}/download_graph/{graph_id}")
        response.raise_for_status()
        return response.content
    except requests.exceptions.RequestException as e:
        st.error(f"Download failed: {str(e)}")
        return None


def main():
    # Header
    st.title("🧠 Knowledge Graph System")
    st.markdown("---")
    
    # Check API status
    api_status = check_api_health()
    
    # Sidebar
    with st.sidebar:
        st.header("System Status")
        if api_status:
            st.success("✅ API Connected")
        else:
            st.error("❌ API Disconnected")
            st.info(f"Ensure backend is running at {API_BASE_URL}")
            return
        
        st.markdown("---")
        st.header("Navigation")
        page = st.radio(
            "Select Page",
            ["Upload Document", "Browse Graphs", "Query Graph"],
            label_visibility="collapsed"
        )
    
    # Main content based on selected page
    if page == "Upload Document":
        show_upload_page()
    elif page == "Browse Graphs":
        show_browse_page()
    elif page == "Query Graph":
        show_query_page()


def show_upload_page():
    """Display document upload interface"""
    st.header("Upload Document")
    st.markdown("Upload a document to create a knowledge graph")
    
    col1, col2 = st.columns([2, 1])
    
    with col1:
        uploaded_file = st.file_uploader(
            "Choose a file",
            type=["pdf", "txt", "docx", "doc"],
            help="Supported formats: PDF, TXT, DOCX"
        )
        
        if uploaded_file:
            st.info(f"**File:** {uploaded_file.name} ({uploaded_file.size / 1024:.2f} KB)")
            
            if st.button("🚀 Create Knowledge Graph", type="primary", use_container_width=True):
                with st.spinner("Processing document and creating knowledge graph..."):
                    result = upload_document(uploaded_file)
                    
                    if result:
                        st.success("✅ Knowledge graph created successfully!")
                        
                        # Store graph_id in session state
                        st.session_state.latest_graph_id = result['graph_id']
                        
                        # Display results
                        col_a, col_b, col_c = st.columns(3)
                        with col_a:
                            st.metric("Entities", result['entities_count'])
                        with col_b:
                            st.metric("Relations", result['relations_count'])
                        with col_c:
                            st.metric("Triples", result['statistics'].get('triples', 0))
                        
                        st.json(result['statistics'])
                        
                        # Show visualization
                        st.subheader("Knowledge Graph Visualization")
                        viz = get_visualization(result['graph_id'])
                        if viz:
                            st.image(viz, use_container_width=True)
    
    with col2:
        st.markdown("### 📋 Instructions")
        st.markdown("""
        1. Upload a document (PDF, TXT, or DOCX)
        2. Click "Create Knowledge Graph"
        3. Wait for processing to complete
        4. View the generated graph
        5. Use the Query tab to explore
        """)
        
        st.markdown("### ℹ️ About")
        st.markdown("""
        The system extracts entities and relationships from your document using:
        - SpaCy NLP
        - OpenAI LLM
        - Pattern matching
        """)


def show_browse_page():
    """Display all active graphs"""
    st.header("Browse Knowledge Graphs")
    
    graphs_data = get_graphs()
    
    if not graphs_data or graphs_data['total'] == 0:
        st.info("No knowledge graphs available. Upload a document to get started.")
        return
    
    st.markdown(f"**Total Graphs:** {graphs_data['total']}")
    
    # Display graphs in expandable sections
    for graph in graphs_data['graphs']:
        with st.expander(f"📊 {graph['filename']} - {graph['graph_id'][:16]}..."):
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown("**Metadata**")
                st.write(f"**Created:** {graph['created_at']}")
                st.write(f"**Entities:** {graph['entities_count']}")
                st.write(f"**Relations:** {graph['relations_count']}")
            
            with col2:
                st.markdown("**Statistics**")
                st.json(graph['statistics'])
            
            # Action buttons
            col_a, col_b, col_c = st.columns(3)
            
            with col_a:
                if st.button("View Visualization", key=f"viz_{graph['graph_id']}"):
                    viz = get_visualization(graph['graph_id'])
                    if viz:
                        st.image(viz, use_container_width=True)
            
            with col_b:
                if st.button("Download RDF", key=f"dl_{graph['graph_id']}"):
                    content = download_graph(graph['graph_id'])
                    if content:
                        st.download_button(
                            "💾 Save File",
                            content,
                            file_name=f"kg_{graph['graph_id']}.ttl",
                            mime="text/turtle",
                            key=f"save_{graph['graph_id']}"
                        )
            
            with col_c:
                if st.button("Select for Query", key=f"sel_{graph['graph_id']}", type="primary"):
                    st.session_state.selected_graph_id = graph['graph_id']
                    st.success(f"Selected: {graph['graph_id'][:16]}...")


def show_query_page():
    """Display query interface"""
    st.header("Query Knowledge Graph")
    
    # Graph selection
    graphs_data = get_graphs()
    
    if not graphs_data or graphs_data['total'] == 0:
        st.warning("No graphs available. Please upload a document first.")
        return
    
    # Create graph selector
    graph_options = {
        f"{g['filename']} ({g['graph_id'][:8]})": g['graph_id'] 
        for g in graphs_data['graphs']
    }
    
    selected_label = st.selectbox(
        "Select Knowledge Graph",
        options=list(graph_options.keys())
    )
    
    selected_graph_id = graph_options[selected_label]
    
    # Display graph info
    graph_info = get_graph_info(selected_graph_id)
    if graph_info:
        col1, col2, col3, col4 = st.columns(4)
        with col1:
            st.metric("Entities", graph_info['entities_count'])
        with col2:
            st.metric("Relations", graph_info['relations_count'])
        with col3:
            st.metric("Triples", graph_info['statistics'].get('triples', 0))
        with col4:
            st.metric("Entity Types", len(graph_info['statistics'].get('entity_types', {})))
    
    st.markdown("---")
    
    # Query tabs
    tab1, tab2, tab3, tab4 = st.tabs([
        "🔍 Semantic Search",
        "💬 Question Answering",
        "🔗 Entity Relations",
        "⚡ SPARQL Query"
    ])
    
    with tab1:
        st.subheader("Semantic Search")
        st.markdown("Search the knowledge graph using natural language")
        
        search_query = st.text_input("Enter search query", placeholder="e.g., artificial intelligence applications")
        top_k = st.slider("Number of results", 1, 20, 5)
        
        if st.button("Search", type="primary"):
            if search_query:
                with st.spinner("Searching..."):
                    results = semantic_search(selected_graph_id, search_query, top_k)
                    
                    if results and results['results']:
                        st.success(f"Found {len(results['results'])} results")
                        
                        for i, result in enumerate(results['results'], 1):
                            with st.container():
                                st.markdown(f"**Result {i}** (Score: {result.get('score', 'N/A')})")
                                st.write(result.get('text', result))
                                st.markdown("---")
                    else:
                        st.info("No results found")
            else:
                st.warning("Please enter a search query")
    
    with tab2:
        st.subheader("Question Answering")
        st.markdown("Ask questions about your document using LLM")
        
        question = st.text_area("Enter your question", placeholder="e.g., What are the main topics discussed?")
        
        if st.button("Get Answer", type="primary"):
            if question:
                with st.spinner("Generating answer..."):
                    response = question_answer(selected_graph_id, question)
                    
                    if response:
                        st.markdown("### Answer")
                        st.info(response['answer'])
                        
                        if response.get('relevant_facts'):
                            with st.expander("View Relevant Facts"):
                                for fact in response['relevant_facts']:
                                    st.write(f"• {fact.get('text', fact)}")
            else:
                st.warning("Please enter a question")
    
    with tab3:
        st.subheader("Entity Relations")
        st.markdown("Explore relationships of specific entities")
        
        if graph_info and graph_info.get('sample_entities'):
            entity_input = st.selectbox(
                "Select or enter entity name",
                options=[""] + graph_info['sample_entities'],
                index=0
            )
            
            if not entity_input:
                entity_input = st.text_input("Or type entity name", placeholder="e.g., OpenAI")
        else:
            entity_input = st.text_input("Enter entity name", placeholder="e.g., OpenAI")
        
        if st.button("Find Relations", type="primary"):
            if entity_input:
                with st.spinner("Searching relations..."):
                    result = entity_relations(selected_graph_id, entity_input)
                    
                    if result and result['relations']:
                        st.success(f"Found {result['count']} relations for '{entity_input}'")
                        
                        # Convert to DataFrame for better display
                        df = pd.DataFrame(result['relations'])
                        st.dataframe(df, use_container_width=True)
                    else:
                        st.info(f"No relations found for '{entity_input}'")
            else:
                st.warning("Please enter an entity name")
    
    with tab4:
        st.subheader("SPARQL Query")
        st.markdown("Execute custom SPARQL queries on the knowledge graph")
        
        # Example queries
        with st.expander("📝 Example Queries"):
            st.code("""
# Get all triples
SELECT ?subject ?predicate ?object
WHERE {
    ?subject ?predicate ?object
}
LIMIT 10

# Get all entities of a specific type
SELECT ?entity
WHERE {
    ?entity rdf:type <http://example.org/PERSON>
}

# Get all relations for an entity
SELECT ?relation ?object
WHERE {
    <http://example.org/OpenAI> ?relation ?object
}
            """, language="sparql")
        
        sparql_query_input = st.text_area(
            "Enter SPARQL query",
            height=200,
            placeholder="SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10"
        )
        
        if st.button("Execute Query", type="primary"):
            if sparql_query_input:
                with st.spinner("Executing query..."):
                    result = sparql_query(selected_graph_id, sparql_query_input)
                    
                    if result and result['results']:
                        st.success(f"Query returned {result['count']} results")
                        
                        # Display as DataFrame
                        df = pd.DataFrame(result['results'])
                        st.dataframe(df, use_container_width=True)
                        
                        # Download option
                        csv = df.to_csv(index=False)
                        st.download_button(
                            "Download Results as CSV",
                            csv,
                            file_name="sparql_results.csv",
                            mime="text/csv"
                        )
                    else:
                        st.info("Query returned no results")
            else:
                st.warning("Please enter a SPARQL query")


if __name__ == "__main__":
    main()