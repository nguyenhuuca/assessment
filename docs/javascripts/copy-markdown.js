document.addEventListener('DOMContentLoaded', function () {
  // Derive raw GitHub URL from the edit button injected by MkDocs Material
  const editLink = document.querySelector('a[title="Edit this page"]');
  if (!editLink) return;

  // https://github.com/user/repo/edit/main/docs/path.md
  // → https://raw.githubusercontent.com/user/repo/main/docs/path.md
  const rawUrl = editLink.href
    .replace('github.com', 'raw.githubusercontent.com')
    .replace('/edit/', '/');

  // Build button
  const btn = document.createElement('button');
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

  btn.addEventListener('mouseenter', () => btn.style.opacity = '0.85');
  btn.addEventListener('mouseleave', () => btn.style.opacity = '1');

  btn.addEventListener('click', async function () {
    try {
      const res = await fetch(rawUrl);
      if (!res.ok) throw new Error(res.status);
      const text = await res.text();
      await navigator.clipboard.writeText(text);
      btn.textContent = '✅ Copied!';
    } catch (_) {
      btn.textContent = '❌ Failed — check CORS or network';
    }
    setTimeout(() => { btn.textContent = '📋 Copy Markdown'; }, 2500);
  });

  // Insert right after the <h1>
  const h1 = document.querySelector('article h1, .md-content h1');
  if (h1) {
    h1.insertAdjacentElement('afterend', btn);
  }
});
