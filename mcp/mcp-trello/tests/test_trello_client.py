"""Tests for Trello API client"""

import pytest
from unittest.mock import Mock, patch
from src.trello_client import TrelloClient


class TestTrelloClient:
    """Test suite for TrelloClient"""

    @pytest.fixture
    def mock_config(self):
        """Create a mock configuration"""
        config = Mock()
        config.base_url = 'https://api.trello.com/1'
        config.get_auth_params.return_value = {
            'key': 'test_key',
            'token': 'test_token'
        }
        return config

    @pytest.fixture
    def client(self, mock_config):
        """Create a TrelloClient instance with mock config"""
        return TrelloClient(mock_config)

    @patch('src.trello_client.requests.request')
    def test_list_boards(self, mock_request, client):
        """Test listing boards"""
        mock_response = Mock()
        mock_response.json.return_value = [
            {'id': '1', 'name': 'Board 1'},
            {'id': '2', 'name': 'Board 2'}
        ]
        mock_response.status_code = 200
        mock_response.raise_for_status = Mock()
        mock_request.return_value = mock_response

        boards = client.list_boards()

        assert len(boards) == 2
        assert boards[0]['name'] == 'Board 1'
        mock_request.assert_called_once()

    @patch('src.trello_client.requests.request')
    def test_list_cards(self, mock_request, client):
        """Test listing cards on a board"""
        mock_response = Mock()
        mock_response.json.return_value = [
            {'id': 'c1', 'name': 'Card 1', 'labels': []},
            {'id': 'c2', 'name': 'Card 2', 'labels': []}
        ]
        mock_response.status_code = 200
        mock_response.raise_for_status = Mock()
        mock_request.return_value = mock_response

        cards = client.list_cards('board123')

        assert len(cards) == 2
        assert cards[0]['name'] == 'Card 1'

    @patch('src.trello_client.requests.request')
    def test_update_card(self, mock_request, client):
        """Test updating a card"""
        mock_response = Mock()
        mock_response.json.return_value = {
            'id': 'card123',
            'name': 'Updated Name',
            'desc': 'Updated Description'
        }
        mock_response.status_code = 200
        mock_response.raise_for_status = Mock()
        mock_request.return_value = mock_response

        result = client.update_card('card123', name='Updated Name', desc='Updated Description')

        assert result['name'] == 'Updated Name'
        assert result['desc'] == 'Updated Description'

    @patch('src.trello_client.requests.request')
    def test_get_cards_by_label(self, mock_request, client):
        """Test filtering cards by label"""
        mock_response = Mock()
        mock_response.json.return_value = [
            {'id': 'c1', 'name': 'Card 1', 'labels': [{'name': 'urgent'}]},
            {'id': 'c2', 'name': 'Card 2', 'labels': [{'name': 'doing'}]},
            {'id': 'c3', 'name': 'Card 3', 'labels': [{'name': 'urgent'}]}
        ]
        mock_response.status_code = 200
        mock_response.raise_for_status = Mock()
        mock_request.return_value = mock_response

        cards = client.get_cards_by_label('board123', 'urgent')

        assert len(cards) == 2
        assert cards[0]['id'] == 'c1'
        assert cards[1]['id'] == 'c3'

    def test_update_card_no_params(self, client):
        """Test that update_card raises error with no parameters"""
        with pytest.raises(ValueError):
            client.update_card('card123')
