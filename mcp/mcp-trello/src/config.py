"""Configuration management for Trello MCP Server"""

import os
from dotenv import load_dotenv


class TrelloConfig:
    """Configuration for Trello API access"""

    def __init__(self):
        # Load environment variables from .env file
        load_dotenv()

        self.api_key = os.getenv('TRELLO_API_KEY')
        self.token = os.getenv('TRELLO_TOKEN')
        self.base_url = 'https://api.trello.com/1'

        # Validate required credentials
        if not self.api_key:
            raise ValueError(
                "TRELLO_API_KEY must be set in environment or .env file. "
                "Get your API key from https://trello.com/power-ups/admin"
            )

        if not self.token:
            raise ValueError(
                "TRELLO_TOKEN must be set in environment or .env file. "
                "Generate a token from https://trello.com/power-ups/admin"
            )

    def get_auth_params(self):
        """Get authentication parameters for API requests"""
        return {
            'key': self.api_key,
            'token': self.token
        }
