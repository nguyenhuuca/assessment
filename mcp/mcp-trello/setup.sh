#!/bin/bash
# Setup script for Trello MCP Server (Linux/Mac)

set -e

echo "==================================="
echo "Trello MCP Server Setup"
echo "==================================="
echo ""

# Check Python version
echo "Checking Python version..."
if ! command -v python3 &> /dev/null; then
    echo "❌ Error: Python 3 is not installed"
    echo "Please install Python 3.10 or higher"
    exit 1
fi

PYTHON_VERSION=$(python3 -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')
REQUIRED_VERSION="3.10"

if [ "$(printf '%s\n' "$REQUIRED_VERSION" "$PYTHON_VERSION" | sort -V | head -n1)" != "$REQUIRED_VERSION" ]; then
    echo "❌ Error: Python $PYTHON_VERSION found, but Python $REQUIRED_VERSION or higher is required"
    exit 1
fi

echo "✅ Python $PYTHON_VERSION found"
echo ""

# Create virtual environment
echo "Creating virtual environment..."
if [ -d "venv" ]; then
    echo "⚠️  Virtual environment already exists. Removing..."
    rm -rf venv
fi

python3 -m venv venv
echo "✅ Virtual environment created"
echo ""

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate
echo "✅ Virtual environment activated"
echo ""

# Upgrade pip
echo "Upgrading pip..."
pip install --upgrade pip > /dev/null 2>&1
echo "✅ pip upgraded"
echo ""

# Install dependencies
echo "Installing dependencies..."
pip install -r requirements.txt
if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to install dependencies"
    exit 1
fi
echo "✅ Dependencies installed"
echo ""

# Check for .env file
echo "Checking configuration..."
if [ ! -f ".env" ]; then
    echo "⚠️  .env file not found. Creating from template..."
    cp .env.example .env
    echo "✅ .env file created"
    echo ""
    echo "========================================="
    echo "⚠️  IMPORTANT: Configure your credentials"
    echo "========================================="
    echo ""
    echo "Please edit .env file and set:"
    echo "  - TRELLO_API_KEY"
    echo "  - TRELLO_TOKEN"
    echo ""
    echo "To get your credentials:"
    echo "  1. Visit: https://trello.com/power-ups/admin"
    echo "  2. Generate your API Key"
    echo "  3. Generate a Token"
    echo ""
    echo "See doc/trello-setup-guide.md for detailed instructions"
    echo ""
    exit 0
else
    echo "✅ .env file found"
fi

# Validate credentials
echo "Validating Trello credentials..."
source .env

if [ -z "$TRELLO_API_KEY" ] || [ "$TRELLO_API_KEY" = "your_api_key_here" ]; then
    echo "❌ Error: TRELLO_API_KEY not configured in .env"
    echo "Please edit .env and set your Trello API key"
    exit 1
fi

if [ -z "$TRELLO_TOKEN" ] || [ "$TRELLO_TOKEN" = "your_token_here" ]; then
    echo "❌ Error: TRELLO_TOKEN not configured in .env"
    echo "Please edit .env and set your Trello token"
    exit 1
fi

# Test Trello API connection
echo "Testing Trello API connection..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    "https://api.trello.com/1/members/me?key=$TRELLO_API_KEY&token=$TRELLO_TOKEN")

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Trello API connection successful"
else
    echo "❌ Error: Trello API connection failed (HTTP $HTTP_CODE)"
    echo "Please check your credentials in .env"
    exit 1
fi

echo ""
echo "==================================="
echo "✅ Setup completed successfully!"
echo "==================================="
echo ""
echo "To start the server:"
echo "  1. Activate virtual environment: source venv/bin/activate"
echo "  2. Run server: python -m src.main"
echo ""
echo "To integrate with Claude Code:"
echo "  - Add server configuration to .mcp.json"
echo "  - See README.md for details"
echo ""
