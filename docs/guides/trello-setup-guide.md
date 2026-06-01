# Trello MCP Server Setup Guide

## Step 1: Get Trello API Credentials

1. **Get API Key:**
   - Visit: https://trello.com/app-key
   - Login to your Trello account
   - Copy your API Key

2. **Get Token:**
   - On the same page, click "Token" link (or manually generate)
   - Or visit: `https://trello.com/1/authorize?expiration=never&scope=read,write&response_type=token&name=Claude%20Code&key=YOUR_API_KEY`
   - Replace `YOUR_API_KEY` with your actual API key
   - Authorize the application
   - Copy the token

3. **Get Board ID:**
   - Open your Trello board in browser
   - URL format: `https://trello.com/b/BOARD_ID/board-name`
   - Or add `.json` to board URL: `https://trello.com/b/BOARD_ID.json`
   - Copy the `id` field

## Step 2: Add to Environment Variables

```bash
# Add to api/.env
TRELLO_API_KEY=your_api_key_here
TRELLO_TOKEN=your_token_here
TRELLO_BOARD_ID=your_board_id_here
```

## Step 3: Test API Access

```bash
# Test list all boards
curl "https://api.trello.com/1/members/me/boards?key=YOUR_KEY&token=YOUR_TOKEN"

# Test list cards on board
curl "https://api.trello.com/1/boards/BOARD_ID/cards?key=YOUR_KEY&token=YOUR_TOKEN"

# Test filter cards by label
curl "https://api.trello.com/1/boards/BOARD_ID/cards?key=YOUR_KEY&token=YOUR_TOKEN" | jq '.[] | select(.labels[].name == "doing")'
```

## Trello API Reference

- API Documentation: https://developer.atlassian.com/cloud/trello/rest/api-group-actions/
- Board endpoints: https://developer.atlassian.com/cloud/trello/rest/api-group-boards/
- Card endpoints: https://developer.atlassian.com/cloud/trello/rest/api-group-cards/
