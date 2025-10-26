#!/bin/bash

# Knowledge Graph System Startup Script
# Starts both FastAPI backend and Streamlit frontend

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Knowledge Graph System Startup${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}Error: Python3 is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Python3 found${NC}"

# Check if required files exist
if [ ! -f "api.py" ]; then
    echo -e "${RED}Error: api.py not found in current directory${NC}"
    exit 1
fi

if [ ! -f "app.py" ]; then
    echo -e "${RED}Error: app.py (Streamlit frontend) not found in current directory${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Required files found${NC}\n"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down services...${NC}"
    kill $FASTAPI_PID $STREAMLIT_PID 2>/dev/null
    echo -e "${GREEN}Services stopped${NC}"
    exit 0
}

# Set trap to cleanup on script exit
trap cleanup SIGINT SIGTERM EXIT

# Create output and logs directories if they don't exist
mkdir -p output logs
echo -e "${GREEN}✓ Output and logs directories ready${NC}\n"

# Start FastAPI backend
echo -e "${BLUE}Starting FastAPI backend on port 8000...${NC}"
python3 -m uvicorn api:app --host 0.0.0.0 --port 8000 --reload > logs/fastapi.log 2>&1 &
FASTAPI_PID=$!

# Wait a bit for FastAPI to start
sleep 3

# Check if FastAPI is running
if ! kill -0 $FASTAPI_PID 2>/dev/null; then
    echo -e "${RED}Error: FastAPI failed to start${NC}"
    echo -e "${YELLOW}Check logs/fastapi.log for details${NC}"
    exit 1
fi

echo -e "${GREEN}✓ FastAPI backend started (PID: $FASTAPI_PID)${NC}"
echo -e "${GREEN}  → API running at http://localhost:8000${NC}\n"

# Start Streamlit frontend
echo -e "${BLUE}Starting Streamlit frontend on port 8501...${NC}"
python3 -m streamlit run app.py --server.port 8501 --server.headless true > logs/streamlit.log 2>&1 &
STREAMLIT_PID=$!

# Wait a bit for Streamlit to start
sleep 3

# Check if Streamlit is running
if ! kill -0 $STREAMLIT_PID 2>/dev/null; then
    echo -e "${RED}Error: Streamlit failed to start${NC}"
    echo -e "${YELLOW}Check logs/streamlit.log for details${NC}"
    kill $FASTAPI_PID 2>/dev/null
    exit 1
fi

echo -e "${GREEN}✓ Streamlit frontend started (PID: $STREAMLIT_PID)${NC}"
echo -e "${GREEN}  → Frontend running at http://localhost:8501${NC}\n"

echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}System is ready!${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "\n${YELLOW}Services:${NC}"
echo -e "  FastAPI Backend:     http://localhost:8000"
echo -e "  API Documentation:   http://localhost:8000/docs"
echo -e "  Streamlit Frontend:  http://localhost:8501"
echo -e "\n${YELLOW}Press Ctrl+C to stop both services${NC}\n"

# Keep script running and show live logs
echo -e "${BLUE}--- Live Logs (Ctrl+C to exit) ---${NC}\n"

# Tail both log files
tail -f logs/fastapi.log logs/streamlit.log