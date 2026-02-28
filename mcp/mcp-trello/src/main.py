#!/usr/bin/env python3
"""Trello MCP Server - Python Implementation

Provides Claude Code with direct access to Trello API

Tools:
- trello_list_boards: List all boards
- trello_list_cards: List cards on a board
- trello_get_card: Get card details
- trello_update_card: Update card properties
- trello_add_comment: Add comment to card
- trello_get_cards_by_label: Get cards with specific label
- trello_watch_label: Poll for cards with specific label
"""

import sys
import json
import asyncio
import logging
from typing import Any

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, TextContent

from .config import TrelloConfig
from .trello_client import TrelloClient

# Configure logging
logging.basicConfig(level=logging.INFO, stream=sys.stderr)
logger = logging.getLogger(__name__)

# Initialize configuration and client
try:
    config = TrelloConfig()
    trello = TrelloClient(config)
except Exception as e:
    logger.error(f"Failed to initialize Trello client: {e}")
    sys.exit(1)

# Create MCP server
app = Server("trello-mcp-server-python")


# Tool definitions
TOOLS = [
    Tool(
        name="trello_list_boards",
        description="List all Trello boards accessible to the authenticated user",
        inputSchema={
            "type": "object",
            "properties": {},
        }
    ),
    Tool(
        name="trello_list_cards",
        description="List all cards on a specific Trello board",
        inputSchema={
            "type": "object",
            "properties": {
                "boardId": {
                    "type": "string",
                    "description": "The ID of the board"
                }
            },
            "required": ["boardId"]
        }
    ),
    Tool(
        name="trello_get_card",
        description="Get detailed information about a specific card",
        inputSchema={
            "type": "object",
            "properties": {
                "cardId": {
                    "type": "string",
                    "description": "The ID of the card"
                }
            },
            "required": ["cardId"]
        }
    ),
    Tool(
        name="trello_update_card",
        description="Update a card's properties (name, description, etc.)",
        inputSchema={
            "type": "object",
            "properties": {
                "cardId": {
                    "type": "string",
                    "description": "The ID of the card"
                },
                "name": {
                    "type": "string",
                    "description": "New name for the card (optional)"
                },
                "desc": {
                    "type": "string",
                    "description": "New description for the card (optional)"
                }
            },
            "required": ["cardId"]
        }
    ),
    Tool(
        name="trello_add_comment",
        description="Add a comment to a card",
        inputSchema={
            "type": "object",
            "properties": {
                "cardId": {
                    "type": "string",
                    "description": "The ID of the card"
                },
                "text": {
                    "type": "string",
                    "description": "The comment text"
                }
            },
            "required": ["cardId", "text"]
        }
    ),
    Tool(
        name="trello_get_cards_by_label",
        description="Get all cards on a board that have a specific label",
        inputSchema={
            "type": "object",
            "properties": {
                "boardId": {
                    "type": "string",
                    "description": "The ID of the board"
                },
                "labelName": {
                    "type": "string",
                    "description": "The name of the label to filter by"
                }
            },
            "required": ["boardId", "labelName"]
        }
    ),
    Tool(
        name="trello_create_card",
        description="Create a new card in a Trello list",
        inputSchema={
            "type": "object",
            "properties": {
                "listId": {"type": "string", "description": "The ID of the list to add the card to"},
                "name": {"type": "string", "description": "Title of the card"},
                "desc": {"type": "string", "description": "Description/body of the card (optional)"},
                "labelIds": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "List of label IDs to attach (optional)"
                }
            },
            "required": ["listId", "name"]
        }
    ),
    Tool(
        name="trello_get_board_lists",
        description="Get all lists on a Trello board",
        inputSchema={
            "type": "object",
            "properties": {
                "boardId": {"type": "string", "description": "The ID of the board"}
            },
            "required": ["boardId"]
        }
    ),
    Tool(
        name="trello_get_board_labels",
        description="Get all labels defined on a Trello board",
        inputSchema={
            "type": "object",
            "properties": {
                "boardId": {"type": "string", "description": "The ID of the board"}
            },
            "required": ["boardId"]
        }
    ),
    Tool(
        name="trello_watch_label",
        description="Poll for cards with a specific label (e.g., 'doing') and return new ones since last check",
        inputSchema={
            "type": "object",
            "properties": {
                "boardId": {
                    "type": "string",
                    "description": "The ID of the board"
                },
                "labelName": {
                    "type": "string",
                    "description": "The name of the label to watch for"
                },
                "sinceTimestamp": {
                    "type": "string",
                    "description": "ISO timestamp - only return cards modified after this time (optional)"
                }
            },
            "required": ["boardId", "labelName"]
        }
    )
]


@app.list_tools()
async def list_tools() -> list[Tool]:
    """List available tools"""
    return TOOLS


@app.call_tool()
async def call_tool(name: str, arguments: Any) -> list[TextContent]:
    """Handle tool calls"""
    try:
        if name == "trello_list_boards":
            boards = trello.list_boards()
            return [TextContent(type="text", text=json.dumps(boards, indent=2))]

        elif name == "trello_list_cards":
            board_id = arguments.get("boardId")
            cards = trello.list_cards(board_id)
            return [TextContent(type="text", text=json.dumps(cards, indent=2))]

        elif name == "trello_get_card":
            card_id = arguments.get("cardId")
            card = trello.get_card(card_id)
            return [TextContent(type="text", text=json.dumps(card, indent=2))]

        elif name == "trello_update_card":
            card_id = arguments.get("cardId")
            name_value = arguments.get("name")
            desc_value = arguments.get("desc")
            card = trello.update_card(card_id, name=name_value, desc=desc_value)
            result = f"Card updated successfully:\n{json.dumps(card, indent=2)}"
            return [TextContent(type="text", text=result)]

        elif name == "trello_add_comment":
            card_id = arguments.get("cardId")
            text = arguments.get("text")
            comment = trello.add_comment(card_id, text)
            result = f"Comment added successfully:\n{json.dumps(comment, indent=2)}"
            return [TextContent(type="text", text=result)]

        elif name == "trello_create_card":
            list_id = arguments.get("listId")
            card_name = arguments.get("name")
            desc_value = arguments.get("desc")
            label_ids = arguments.get("labelIds")
            card = trello.create_card(list_id, card_name, desc=desc_value, label_ids=label_ids)
            result = f"Card created: {card.get('shortUrl', '')}\n{json.dumps(card, indent=2)}"
            return [TextContent(type="text", text=result)]

        elif name == "trello_get_board_lists":
            board_id = arguments.get("boardId")
            lists = trello.get_board_lists(board_id)
            return [TextContent(type="text", text=json.dumps(lists, indent=2))]

        elif name == "trello_get_board_labels":
            board_id = arguments.get("boardId")
            labels = trello.get_board_labels(board_id)
            return [TextContent(type="text", text=json.dumps(labels, indent=2))]

        elif name == "trello_get_cards_by_label":
            board_id = arguments.get("boardId")
            label_name = arguments.get("labelName")
            cards = trello.get_cards_by_label(board_id, label_name)
            return [TextContent(type="text", text=json.dumps(cards, indent=2))]

        elif name == "trello_watch_label":
            board_id = arguments.get("boardId")
            label_name = arguments.get("labelName")
            since_timestamp = arguments.get("sinceTimestamp")
            result = trello.watch_label(board_id, label_name, since_timestamp)
            return [TextContent(type="text", text=json.dumps(result, indent=2))]

        else:
            raise ValueError(f"Unknown tool: {name}")

    except Exception as e:
        logger.error(f"Error executing tool {name}: {e}")
        return [TextContent(type="text", text=f"Error: {str(e)}")]


async def main():
    """Run the MCP server"""
    logger.info("Starting Trello MCP Server (Python)")
    async with stdio_server() as (read_stream, write_stream):
        await app.run(read_stream, write_stream, app.create_initialization_options())


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Server stopped by user")
    except Exception as e:
        logger.error(f"Server error: {e}")
        sys.exit(1)
