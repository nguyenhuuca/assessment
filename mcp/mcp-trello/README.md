# Trello MCP Server (Python)

A Python implementation of the Trello MCP (Model Context Protocol) server that provides Claude Code with direct access to the Trello API.

## Overview

This MCP server enables Claude Code to interact with Trello boards, cards, labels, and comments directly through a set of standardized tools. It's built with Python 3.10+ and uses the MCP SDK for seamless integration.

### Features

- **Board Management**: List all accessible Trello boards
- **Card Operations**: Read, update, and manage cards
- **Label Filtering**: Find cards by label and watch for changes
- **Comments**: Add comments to cards
- **Real-time Polling**: Watch labels for new activity

### Available Tools

1. **trello_list_boards** - List all boards accessible to the authenticated user
2. **trello_list_cards** - List all cards on a specific board
3. **trello_get_card** - Get detailed information about a specific card
4. **trello_update_card** - Update a card's name or description
5. **trello_add_comment** - Add a comment to a card
6. **trello_get_cards_by_label** - Get all cards with a specific label
7. **trello_watch_label** - Poll for cards with a label, with optional time filtering

## Prerequisites

- **Python 3.10+** (Python 3.10.10 or higher recommended)
- **Trello Account** with API access
- **Trello API Credentials**:
  - API Key
  - API Token

## Installation

### Automated Setup

#### Linux/Mac
```bash
cd mcp/mcp-trello
chmod +x setup.sh
./setup.sh
```

#### Windows
```cmd
cd mcp\mcp-trello
setup.bat
```

The setup script will:
1. Check Python version (requires 3.10+)
2. Create a virtual environment
3. Install all dependencies
4. Create `.env` file from template (if not exists)
5. Validate Trello API credentials (if configured)
6. Test API connection

### Manual Setup

If you prefer manual installation:

```bash
# Create virtual environment
python3 -m venv venv

# Activate virtual environment
# Linux/Mac:
source venv/bin/activate
# Windows:
venv\Scripts\activate.bat

# Install dependencies
pip install -r requirements.txt

# Create .env file
cp .env.example .env
# Edit .env and add your credentials
```

## Configuration

### Getting Trello Credentials

1. **Get API Key:**
   - Visit https://trello.com/power-ups/admin
   - Click "New" to create a new Power-Up (or use existing)
   - Copy your API Key

2. **Generate Token:**
   - On the same page, click "Token" or "Generate a Token"
   - Authorize the application
   - Copy the generated token

For detailed instructions, see: `doc/trello-setup-guide.md`

### Configure Environment Variables

Edit the `.env` file in the `mcp/mcp-trello/` directory:

```env
TRELLO_API_KEY=your_actual_api_key
TRELLO_TOKEN=your_actual_token
TRELLO_BOARD_ID=your_board_id_optional
```

⚠️ **Important:** Never commit the `.env` file to version control!

## Usage

### Running the Server Locally

```bash
# Activate virtual environment
source venv/bin/activate  # Linux/Mac
# or
venv\Scripts\activate.bat  # Windows

# Run the server
python -m src.main
```

The server will start on stdio transport and log to stderr:
```
INFO:__main__:Starting Trello MCP Server (Python)
```

### Integration with Claude Code

Add the server configuration to your `.mcp.json` file in the project root:

```json
{
  "mcpServers": {
    "trello-python": {
      "command": "python",
      "args": ["-m", "src.main"],
      "cwd": "D:\\DO\\assessment\\mcp\\mcp-trello",
      "env": {
        "PYTHONPATH": "D:\\DO\\assessment\\mcp\\mcp-trello",
        "TRELLO_API_KEY": "${TRELLO_API_KEY}",
        "TRELLO_TOKEN": "${TRELLO_TOKEN}"
      }
    }
  }
}
```

**Note:** Adjust the `cwd` path to match your installation directory.

### Tool Examples

Once integrated with Claude Code, you can use natural language commands:

- "List all my Trello boards"
- "Show me all cards on board [board_id]"
- "Update card [card_id] with description: [new description]"
- "Add a comment to card [card_id]: [comment text]"
- "Find all cards with label 'doing' on board [board_id]"
- "Watch for changes on cards with label 'urgent' since [timestamp]"

## Project Structure

```
mcp/mcp-trello/
├── .env                    # Environment variables (not in git)
├── .env.example            # Environment template
├── .gitignore             # Git ignore patterns
├── README.md              # This file
├── requirements.txt       # Python dependencies
├── setup.sh              # Setup script (Linux/Mac)
├── setup.bat             # Setup script (Windows)
├── src/
│   ├── __init__.py       # Package initialization
│   ├── main.py           # MCP server entry point
│   ├── config.py         # Configuration management
│   └── trello_client.py  # Trello API wrapper
└── tests/
    ├── __init__.py
    ├── test_trello_client.py
    └── test_tools.py
```

## Development

### Running Tests

```bash
# Activate virtual environment
source venv/bin/activate

# Run tests
pytest tests/

# Run with coverage
pytest --cov=src tests/
```

### Adding New Tools

1. Add a new method to `TrelloClient` in `src/trello_client.py`
2. Define the tool schema in `TOOLS` list in `src/main.py`
3. Add the tool handler in `call_tool()` function in `src/main.py`
4. Update this README with the new tool documentation

### Code Style

- Follow PEP 8 style guidelines
- Use type hints for function signatures
- Add docstrings for all public functions
- Keep functions focused and single-purpose

## Troubleshooting

### Common Issues

#### "TRELLO_API_KEY and TRELLO_TOKEN must be set"

**Solution:** Make sure you've created a `.env` file and configured your credentials:
```bash
cp .env.example .env
# Edit .env and add your credentials
```

#### "Trello API error: 401 Unauthorized"

**Solution:** Your API key or token is invalid. Double-check your credentials:
1. Visit https://trello.com/power-ups/admin
2. Regenerate your token if needed
3. Update `.env` with new credentials

#### "Module not found" errors

**Solution:** Make sure you've activated the virtual environment:
```bash
source venv/bin/activate  # Linux/Mac
venv\Scripts\activate.bat  # Windows
```

#### Server doesn't start in Claude Code

**Solution:** Check the following:
1. Verify `.mcp.json` path is correct (especially `cwd`)
2. Ensure `PYTHONPATH` is set in `.mcp.json`
3. Check Claude Code logs for error messages
4. Test server manually first: `python -m src.main`

#### "No module named 'src'"

**Solution:** Make sure `PYTHONPATH` is set correctly. When running manually:
```bash
cd mcp/mcp-trello
export PYTHONPATH=$(pwd)  # Linux/Mac
set PYTHONPATH=%cd%  # Windows
python -m src.main
```

### Debug Mode

To enable detailed logging, set the log level in `src/main.py`:

```python
logging.basicConfig(level=logging.DEBUG, stream=sys.stderr)
```

### Testing API Connection

Test your Trello credentials manually:

```bash
curl "https://api.trello.com/1/members/me?key=YOUR_KEY&token=YOUR_TOKEN"
```

## Comparison with Node.js Version

This Python implementation provides the same functionality as the Node.js version at `scripts/trello-mcp-server.js`:

| Feature | Node.js | Python |
|---------|---------|--------|
| All 7 tools | ✅ | ✅ |
| Stdio transport | ✅ | ✅ |
| Environment config | ✅ | ✅ |
| Error handling | ✅ | ✅ |
| JSON response formatting | ✅ | ✅ |

Choose based on your preference or project requirements:
- **Node.js**: Faster startup, native JSON handling
- **Python**: Better ecosystem for data processing, easier debugging

## Resources

- **MCP SDK Documentation**: https://github.com/anthropics/mcp
- **Trello API Documentation**: https://developer.atlassian.com/cloud/trello/rest/
- **Project Setup Guide**: `doc/trello-setup-guide.md`
- **Node.js Implementation**: `scripts/trello-mcp-server.js`

## License

This implementation follows the project's license. See the root LICENSE file for details.

## Support

For issues or questions:
1. Check the Troubleshooting section above
2. Review `doc/trello-setup-guide.md`
3. Check Claude Code MCP documentation
4. Create an issue in the project repository

## Version History

- **1.0.0** (2026-02-13) - Initial Python implementation
  - All 7 tools from Node.js version
  - Virtual environment support
  - Automated setup scripts
  - Comprehensive documentation
