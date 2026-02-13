"""Trello API client wrapper"""

import requests
from typing import Dict, List, Any, Optional
from datetime import datetime


class TrelloClient:
    """Client for interacting with Trello API"""

    def __init__(self, config):
        self.config = config
        self.base_url = config.base_url
        self.auth_params = config.get_auth_params()

    def _make_request(self, endpoint: str, method: str = 'GET', data: Optional[Dict] = None) -> Any:
        """Make authenticated request to Trello API"""
        url = f"{self.base_url}{endpoint}"

        try:
            response = requests.request(
                method=method,
                url=url,
                params=self.auth_params,
                json=data,
                headers={'Content-Type': 'application/json'} if data else None
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.HTTPError as e:
            raise Exception(f"Trello API error: {e.response.status_code} {e.response.reason}")
        except requests.exceptions.RequestException as e:
            raise Exception(f"Request failed: {str(e)}")

    def list_boards(self) -> List[Dict[str, Any]]:
        """List all boards accessible to the authenticated user"""
        return self._make_request('/members/me/boards')

    def list_cards(self, board_id: str) -> List[Dict[str, Any]]:
        """List all cards on a specific board"""
        return self._make_request(f'/boards/{board_id}/cards')

    def get_card(self, card_id: str) -> Dict[str, Any]:
        """Get detailed information about a specific card"""
        return self._make_request(f'/cards/{card_id}?fields=all')

    def update_card(self, card_id: str, name: Optional[str] = None, desc: Optional[str] = None) -> Dict[str, Any]:
        """Update a card's properties"""
        updates = {}
        if name is not None:
            updates['name'] = name
        if desc is not None:
            updates['desc'] = desc

        if not updates:
            raise ValueError("At least one of 'name' or 'desc' must be provided")

        return self._make_request(f'/cards/{card_id}', method='PUT', data=updates)

    def add_comment(self, card_id: str, text: str) -> Dict[str, Any]:
        """Add a comment to a card"""
        return self._make_request(
            f'/cards/{card_id}/actions/comments',
            method='POST',
            data={'text': text}
        )

    def get_cards_by_label(self, board_id: str, label_name: str) -> List[Dict[str, Any]]:
        """Get all cards on a board that have a specific label"""
        cards = self.list_cards(board_id)

        # Filter cards by label name (case-insensitive)
        filtered_cards = [
            card for card in cards
            if any(label['name'].lower() == label_name.lower() for label in card.get('labels', []))
        ]

        return filtered_cards

    def watch_label(self, board_id: str, label_name: str, since_timestamp: Optional[str] = None) -> Dict[str, Any]:
        """
        Poll for cards with a specific label, optionally filtered by modification time

        Args:
            board_id: The ID of the board
            label_name: The name of the label to watch
            since_timestamp: ISO timestamp - only return cards modified after this time

        Returns:
            Dictionary with 'found' count, 'cards' list, and 'checkedAt' timestamp
        """
        cards = self.list_cards(board_id)

        # Filter by label name (case-insensitive)
        filtered_cards = [
            card for card in cards
            if any(label['name'].lower() == label_name.lower() for label in card.get('labels', []))
        ]

        # Filter by timestamp if provided
        if since_timestamp:
            since = datetime.fromisoformat(since_timestamp.replace('Z', '+00:00'))
            filtered_cards = [
                card for card in filtered_cards
                if datetime.fromisoformat(card['dateLastActivity'].replace('Z', '+00:00')) > since
            ]

        return {
            'found': len(filtered_cards),
            'cards': filtered_cards,
            'checkedAt': datetime.utcnow().isoformat() + 'Z'
        }
