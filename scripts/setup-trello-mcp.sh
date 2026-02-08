#!/bin/bash
# Setup Trello MCP Server

set -e

echo "🔧 Setting up Trello MCP Server..."

# Check if in scripts directory
if [ ! -f "package.json" ]; then
    echo "Error: Run this script from the scripts/ directory"
    exit 1
fi

# Check for Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js first."
    exit 1
fi

# Check for npm
if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed. Please install npm first."
    exit 1
fi

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Check for environment variables
if [ -z "$TRELLO_API_KEY" ] || [ -z "$TRELLO_TOKEN" ]; then
    echo ""
    echo "⚠️  Environment variables not set!"
    echo ""
    echo "Please set the following environment variables:"
    echo "  export TRELLO_API_KEY=your_api_key"
    echo "  export TRELLO_TOKEN=your_token"
    echo ""
    echo "To get your credentials:"
    echo "  1. Visit: https://trello.com/app-key"
    echo "  2. Copy your API Key"
    echo "  3. Click 'Token' link to generate a token"
    echo ""
    exit 1
fi

# Test connection
echo "🔍 Testing Trello API connection..."
response=$(curl -s -w "%{http_code}" -o /tmp/trello-test.json \
    "https://api.trello.com/1/members/me/boards?key=$TRELLO_API_KEY&token=$TRELLO_TOKEN")

if [ "$response" = "200" ]; then
    board_count=$(jq length /tmp/trello-test.json 2>/dev/null || echo "0")
    echo "✅ Successfully connected to Trello!"
    echo "   Found $board_count boards"
    rm -f /tmp/trello-test.json
else
    echo "❌ Failed to connect to Trello (HTTP $response)"
    echo "   Please check your API key and token"
    exit 1
fi

echo ""
echo "✅ Setup complete!"
echo ""
echo "The Trello MCP server is ready to use with Claude Code."
echo ""
echo "Available tools:"
echo "  - trello_list_boards"
echo "  - trello_list_cards"
echo "  - trello_get_card"
echo "  - trello_update_card"
echo "  - trello_add_comment"
echo "  - trello_get_cards_by_label"
echo "  - trello_watch_label"
echo ""
echo "To test manually:"
echo "  node trello-mcp-server.js"
