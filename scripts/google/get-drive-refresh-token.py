"""
One-off helper: obtain a Google OAuth refresh token for the Drive uploader (ADR-0019 D2).

Usage (on your PC, not the server):
    python scripts/google/get-drive-refresh-token.py path/to/client_secret.json

Opens a browser for consent, then writes the token to ./drive-oauth.env (NOT printed).
Copy the 3 lines into the server's /opt/funnyapp/.env, then delete drive-oauth.env.
Only the Python standard library is used.
"""
import http.server
import json
import secrets
import sys
import threading
import urllib.parse
import urllib.request
import webbrowser

SCOPE = "https://www.googleapis.com/auth/drive"
PORT = 8765
REDIRECT = f"http://127.0.0.1:{PORT}/"
OUT = "drive-oauth.env"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    cfg = json.load(open(sys.argv[1], encoding="utf-8"))
    client = cfg.get("installed") or cfg.get("web")
    if not client:
        sys.exit("client_secret.json must be an OAuth client (Desktop app)")
    state = secrets.token_urlsafe(16)
    result = {}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if q.get("state", [""])[0] == state and "code" in q:
                result["code"] = q["code"][0]
                msg = "OK - you can close this tab."
            else:
                result["error"] = q.get("error", ["invalid state"])[0]
                msg = "Failed: " + result["error"]
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(msg.encode())

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", PORT), Handler)
    threading.Thread(target=server.handle_request, daemon=True).start()

    url = "https://accounts.google.com/o/oauth2/v2/auth?" + urllib.parse.urlencode({
        "client_id": client["client_id"],
        "redirect_uri": REDIRECT,
        "response_type": "code",
        "scope": SCOPE,
        "access_type": "offline",
        "prompt": "consent",
        "state": state,
    })
    print("Opening browser for consent. If it does not open, visit:\n" + url)
    webbrowser.open(url)
    server.timeout = 300
    while not result:
        server.handle_request()
    if "code" not in result:
        sys.exit("Consent failed: " + result["error"])

    body = urllib.parse.urlencode({
        "code": result["code"],
        "client_id": client["client_id"],
        "client_secret": client["client_secret"],
        "redirect_uri": REDIRECT,
        "grant_type": "authorization_code",
    }).encode()
    token = json.load(urllib.request.urlopen(urllib.request.Request(client["token_uri"], data=body)))
    if "refresh_token" not in token:
        sys.exit("No refresh_token returned (revoke the app at myaccount.google.com/permissions and retry)")

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(f"GOOGLE_OAUTH_CLIENT_ID={client['client_id']}\n")
        f.write(f"GOOGLE_OAUTH_CLIENT_SECRET={client['client_secret']}\n")
        f.write(f"GOOGLE_OAUTH_REFRESH_TOKEN={token['refresh_token']}\n")
    print(f"Saved to {OUT} (not printed). Copy into the server .env, then delete this file.")


if __name__ == "__main__":
    main()
