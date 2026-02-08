#!/usr/bin/env node

/**
 * Trello MCP Server
 *
 * Provides Claude Code with direct access to Trello API
 *
 * Tools:
 * - trello_list_boards: List all boards
 * - trello_list_cards: List cards on a board
 * - trello_get_card: Get card details
 * - trello_update_card: Update card (description, etc.)
 * - trello_add_comment: Add comment to card
 * - trello_get_cards_by_label: Get cards with specific label
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

// Trello API configuration
const TRELLO_API_KEY = process.env.TRELLO_API_KEY;
const TRELLO_TOKEN = process.env.TRELLO_TOKEN;
const TRELLO_BASE_URL = "https://api.trello.com/1";

if (!TRELLO_API_KEY || !TRELLO_TOKEN) {
  console.error("Error: TRELLO_API_KEY and TRELLO_TOKEN must be set");
  process.exit(1);
}

// Helper function to make Trello API calls
async function trelloFetch(endpoint, options = {}) {
  const url = new URL(`${TRELLO_BASE_URL}${endpoint}`);
  url.searchParams.append("key", TRELLO_API_KEY);
  url.searchParams.append("token", TRELLO_TOKEN);

  const response = await fetch(url, options);

  if (!response.ok) {
    throw new Error(`Trello API error: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

// Create MCP server
const server = new Server(
  {
    name: "trello-mcp-server",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// List available tools
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "trello_list_boards",
        description: "List all Trello boards accessible to the authenticated user",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
      {
        name: "trello_list_cards",
        description: "List all cards on a specific Trello board",
        inputSchema: {
          type: "object",
          properties: {
            boardId: {
              type: "string",
              description: "The ID of the board",
            },
          },
          required: ["boardId"],
        },
      },
      {
        name: "trello_get_card",
        description: "Get detailed information about a specific card",
        inputSchema: {
          type: "object",
          properties: {
            cardId: {
              type: "string",
              description: "The ID of the card",
            },
          },
          required: ["cardId"],
        },
      },
      {
        name: "trello_update_card",
        description: "Update a card's properties (name, description, etc.)",
        inputSchema: {
          type: "object",
          properties: {
            cardId: {
              type: "string",
              description: "The ID of the card",
            },
            name: {
              type: "string",
              description: "New name for the card (optional)",
            },
            desc: {
              type: "string",
              description: "New description for the card (optional)",
            },
          },
          required: ["cardId"],
        },
      },
      {
        name: "trello_add_comment",
        description: "Add a comment to a card",
        inputSchema: {
          type: "object",
          properties: {
            cardId: {
              type: "string",
              description: "The ID of the card",
            },
            text: {
              type: "string",
              description: "The comment text",
            },
          },
          required: ["cardId", "text"],
        },
      },
      {
        name: "trello_get_cards_by_label",
        description: "Get all cards on a board that have a specific label",
        inputSchema: {
          type: "object",
          properties: {
            boardId: {
              type: "string",
              description: "The ID of the board",
            },
            labelName: {
              type: "string",
              description: "The name of the label to filter by",
            },
          },
          required: ["boardId", "labelName"],
        },
      },
      {
        name: "trello_watch_label",
        description: "Poll for cards with a specific label (e.g., 'doing') and return new ones since last check",
        inputSchema: {
          type: "object",
          properties: {
            boardId: {
              type: "string",
              description: "The ID of the board",
            },
            labelName: {
              type: "string",
              description: "The name of the label to watch for",
            },
            sinceTimestamp: {
              type: "string",
              description: "ISO timestamp - only return cards modified after this time (optional)",
            },
          },
          required: ["boardId", "labelName"],
        },
      },
    ],
  };
});

// Handle tool calls
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    switch (name) {
      case "trello_list_boards": {
        const boards = await trelloFetch("/members/me/boards");
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(boards, null, 2),
            },
          ],
        };
      }

      case "trello_list_cards": {
        const { boardId } = args;
        const cards = await trelloFetch(`/boards/${boardId}/cards`);
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(cards, null, 2),
            },
          ],
        };
      }

      case "trello_get_card": {
        const { cardId } = args;
        const card = await trelloFetch(`/cards/${cardId}?fields=all`);
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(card, null, 2),
            },
          ],
        };
      }

      case "trello_update_card": {
        const { cardId, name, desc } = args;
        const updates = {};
        if (name) updates.name = name;
        if (desc) updates.desc = desc;

        const card = await trelloFetch(`/cards/${cardId}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(updates),
        });

        return {
          content: [
            {
              type: "text",
              text: `Card updated successfully:\n${JSON.stringify(card, null, 2)}`,
            },
          ],
        };
      }

      case "trello_add_comment": {
        const { cardId, text } = args;
        const comment = await trelloFetch(`/cards/${cardId}/actions/comments`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text }),
        });

        return {
          content: [
            {
              type: "text",
              text: `Comment added successfully:\n${JSON.stringify(comment, null, 2)}`,
            },
          ],
        };
      }

      case "trello_get_cards_by_label": {
        const { boardId, labelName } = args;
        const cards = await trelloFetch(`/boards/${boardId}/cards`);

        const filteredCards = cards.filter((card) =>
          card.labels.some((label) => label.name.toLowerCase() === labelName.toLowerCase())
        );

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(filteredCards, null, 2),
            },
          ],
        };
      }

      case "trello_watch_label": {
        const { boardId, labelName, sinceTimestamp } = args;
        const cards = await trelloFetch(`/boards/${boardId}/cards`);

        let filteredCards = cards.filter((card) =>
          card.labels.some((label) => label.name.toLowerCase() === labelName.toLowerCase())
        );

        // Filter by timestamp if provided
        if (sinceTimestamp) {
          const since = new Date(sinceTimestamp);
          filteredCards = filteredCards.filter((card) => {
            const modified = new Date(card.dateLastActivity);
            return modified > since;
          });
        }

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  found: filteredCards.length,
                  cards: filteredCards,
                  checkedAt: new Date().toISOString(),
                },
                null,
                2
              ),
            },
          ],
        };
      }

      default:
        throw new Error(`Unknown tool: ${name}`);
    }
  } catch (error) {
    return {
      content: [
        {
          type: "text",
          text: `Error: ${error.message}`,
        },
      ],
      isError: true,
    };
  }
});

// Start server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Trello MCP server running on stdio");
}

main().catch((error) => {
  console.error("Server error:", error);
  process.exit(1);
});
