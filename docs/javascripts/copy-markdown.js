document.addEventListener('DOMContentLoaded', function () {
  // Only show on Claude Workspace pages
  if (!window.location.pathname.includes('/claude/')) return;

  var rawUrl = getRawUrl();
  if (!rawUrl) return;

  var btn = document.createElement('button');
  btn.textContent = '📋 Copy Markdown';
  btn.title = 'Copy the raw Markdown source of this page';
  btn.style.cssText = [
    'display:inline-flex',
    'align-items:center',
    'gap:0.4rem',
    'margin:0.75rem 0 1.25rem 0',
    'padding:0.4rem 0.9rem',
    'font-size:0.8rem',
    'font-weight:600',
    'border:none',
    'border-radius:4px',
    'cursor:pointer',
    'background:var(--md-primary-fg-color)',
    'color:var(--md-primary-bg-color)',
    'transition:opacity .15s',
  ].join(';');

  btn.addEventListener('mouseenter', function () { btn.style.opacity = '0.85'; });
  btn.addEventListener('mouseleave', function () { btn.style.opacity = '1'; });

  btn.addEventListener('click', async function () {
    try {
      var res = await fetch(rawUrl);
      if (!res.ok) throw new Error(res.status);
      var text = await res.text();
      await navigator.clipboard.writeText(text);
      btn.textContent = '✅ Copied!';
    } catch (_) {
      // Fallback: open raw URL in new tab so user can copy manually
      window.open(rawUrl, '_blank');
      btn.textContent = '↗ Opened raw file';
    }
    setTimeout(function () { btn.textContent = '📋 Copy Markdown'; }, 2500);
  });

  var h1 = document.querySelector('article h1, .md-content h1');
  if (h1) h1.insertAdjacentElement('afterend', btn);
});

function getRawUrl() {
  var REPO_RAW = 'https://raw.githubusercontent.com/nguyenhuuca/assessment/main/docs';

  // Primary: derive from the edit button rendered by MkDocs Material
  // (requires content.action.edit feature + edit_uri in mkdocs.yml)
  var editLink = document.querySelector('a[title="Edit this page"]');
  if (editLink && editLink.href) {
    // https://github.com/user/repo/edit/main/docs/path.md
    // → https://raw.githubusercontent.com/user/repo/main/docs/path.md
    return editLink.href
      .replace('github.com', 'raw.githubusercontent.com')
      .replace('/edit/', '/');
  }

  // Fallback: construct from window.location.pathname
  // Works when the site is served at root (local dev or GitHub Pages with repo name stripped)
  var path = window.location.pathname
    .replace(/^\/assessment/, '')   // strip GitHub Pages repo prefix
    .replace(/\/$/, '')             // strip trailing slash
    .replace(/^\//, '');            // strip leading slash

  if (!path) return REPO_RAW + '/README.md';

  // MkDocs turns "foo/bar/" into "foo/bar/index.html" — map back to foo/bar.md
  // If path ends with a known directory-style segment, try .md directly
  return REPO_RAW + '/' + path + '.md';
}
