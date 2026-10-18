# canh-labs.com landing page

Static page (HTML + CSS, no JS) served by nginx from `/var/www/canh-labs.com/html` on the app VM
(server block `sites-enabled/canh-labs.com`, which also serves `error_404.html` for 404s).

- `index.html`, `style.css`: the page; fonts from Google Fonts (Newsreader, Inter, JetBrains Mono, all with Vietnamese glyphs)
- `error_404.html`: 404 page, reuses `/style.css`
- `img/`: project screenshots as WebP (source PNGs live in each project repo)

## Preview

```bash
cd landing/canh-labs.com && python -m http.server 8000   # http://localhost:8000
```

## Deploy

Uses the SSH settings in `scripts/monitoring/nginx-alert/server.config` (git-ignored). Backs up the
current site, uploads into `html.new`, then swaps it in.

```bash
cd landing/canh-labs.com
. ../../scripts/monitoring/nginx-alert/server.config
SSH="ssh -i <key> -p $SSH_PORT $SSH_USER@$SSH_HOST"
$SSH 'cd /var/www/canh-labs.com && cp -a html html.bak-$(date +%F) && rm -rf html.new && mkdir html.new'
tar -cf - index.html style.css error_404.html img | $SSH 'cd /var/www/canh-labs.com && tar -xf - -C html.new && mv html html.old && mv html.new html && rm -rf html.old'
```

Then check `https://canh-labs.com/` and a missing path (expects the 404 page). Cloudflare caches
`style.css` and images, so purge them in Cloudflare after changing those files.
