"""Fails when a built page shows a list or code fence as run-on paragraph text (see scripts/mkdocs_hooks.py).

Usage: python scripts/check-docs-lists.py [site_dir]
"""
import pathlib
import re
import sys

site = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "site")
broken = []
for page in sorted(site.rglob("*.html")):
    for para in re.findall(r"<p>(.*?)</p>", page.read_text(encoding="utf8"), re.S):
        if re.search(r"\n\s*(- |\d+\. |```)", para):
            text = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", para))
            broken.append(f"{page.relative_to(site)}: {text[:100]}")
sys.stdout.reconfigure(encoding="utf-8")  # page text has arrows and Vietnamese; Windows consoles default to cp1252
print("\n".join(broken) or f"ok: no run-on lists in {site}")
sys.exit(1 if broken else 0)
