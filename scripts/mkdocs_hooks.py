"""MkDocs hook: read GitHub-style lists the way GitHub does.

The payload and docs are written for GitHub, where a list may follow a paragraph line directly and nested content
is indented by 2 spaces. Python-Markdown needs a blank line before a list and 4 spaces per nesting level, so without
this hook those lists render as one run-on paragraph.
"""
import re

LIST_ITEM = re.compile(r"^(\s*)([-*+]|\d+[.)])\s")
FENCE = re.compile(r"^(\s*)(`{3,}|~{3,})")


def indent(line):
    return len(line) - len(line.lstrip(" "))


def normalize(markdown):
    out = []
    fence = None  # (marker, indent in the source) while inside a fenced block
    in_list = False
    item_text = False
    for line in markdown.split("\n"):
        if fence:
            marker, offset = fence
            if line.strip().startswith(marker) and not line.strip().strip(marker[0]):
                fence = None
            if offset:
                # move only the fence's offset; the code keeps its own indentation
                body = line[offset:] if not line[:offset].strip() else line.lstrip(" ")
                line = " " * (offset * 2) + body
            out.append(line)
            continue
        if not line.strip():
            item_text = False
            out.append(line)
            continue
        lead = indent(line)
        item = LIST_ITEM.match(line)
        if lead == 0 and not item:
            in_list = False
        if item:
            in_list = True
        if in_list and lead:
            line = " " * (lead * 2) + line.lstrip(" ")
        if item:
            prev = out[-1] if out else ""
            # an item right after a paragraph opens a new list; after an item's own wrapped text it does not
            if prev.strip() and not item_text:
                out.append("")
        fenced = FENCE.match(line)
        if fenced:
            fence = (fenced.group(2), lead if in_list else 0)
        # is the current text block an item line plus its wrapped continuation?
        item_text = bool(item) or (item_text and bool(out) and out[-1].strip() != "" and not fenced)
        out.append(line)
    return "\n".join(out)


QUOTE = re.compile(r"^> ?")


def normalize_quotes(markdown):
    # lists inside a blockquote get the same treatment: normalize the quoted text, then quote it again
    out, quoted, fence = [], [], None
    for line in markdown.split("\n") + [""]:
        fenced = FENCE.match(line)
        if fenced and not quoted:
            marker = fenced.group(2)
            fence = None if fence and line.strip().startswith(fence) else (fence or marker)
        if QUOTE.match(line) and not fence:
            quoted.append(QUOTE.sub("", line, count=1))
            continue
        if quoted:
            out.extend(("> " + q).rstrip() for q in normalize(normalize_quotes("\n".join(quoted))).split("\n"))
            quoted = []
        out.append(line)
    return "\n".join(out[:-1])


def on_page_markdown(markdown, **kwargs):
    return normalize(normalize_quotes(markdown))
