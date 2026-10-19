#!/usr/bin/env python3
"""Checks the GitHub Pages site in site/ before it deploys.

Every local link and asset must exist, and nothing may load from another
origin (fonts, scripts, styles, images): the site, like the app, makes no
third-party requests. Links out to other sites are fine.

Each stylesheet link carries the stylesheet's content hash (`style.css?v=…`),
so a page never pairs with a stale cached copy: Pages lets browsers cache
for ten minutes, and a new page on an old stylesheet renders broken. Run
with --fix to write the current hashes into the pages after a CSS change.
Standard library only; run it from the repository root.
"""
import hashlib
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote

SITE = Path("site")
BASE = "/bf-12c/"  # the Pages path, which 404.html links from


class Refs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links, self.loads, self.styles, self.ids = [], [], [], set()

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag == "a" and a.get("href"):
            self.links.append(a["href"])
        elif tag == "link" and a.get("href"):
            rel = a.get("rel", "")
            # canonical is a URL, not a load.
            (self.links if rel == "canonical" else self.loads).append(a["href"])
            if rel == "stylesheet":
                self.styles.append(a["href"])
        elif tag in ("img", "script", "source", "iframe", "video", "audio") and a.get("src"):
            self.loads.append(a["src"])


def local_target(page: Path, ref: str) -> Path | None:
    """The file a same-site reference points at, or None for another site."""
    parts = urlsplit(ref)
    if parts.scheme or parts.netloc:
        return None
    path = unquote(parts.path)
    if not path:
        return page
    if path.startswith("/"):
        if not path.startswith(BASE):
            return SITE / "__outside_base__"
        target = SITE / path[len(BASE):]
    else:
        target = page.parent / path
    if path.endswith("/") or target.is_dir():
        target = target / "index.html"
    return target


def version(css: Path) -> str:
    return hashlib.sha256(css.read_bytes()).hexdigest()[:10]


def fix_versions(pages: list[Path]) -> None:
    """Rewrites each local stylesheet href to carry its file's current hash."""
    for page in pages:
        html = page.read_text(encoding="utf-8")

        def stamp(m: re.Match) -> str:
            path = m.group(2).split("?", 1)[0]
            target = local_target(page, path)
            if target is None or not target.is_file():
                return m.group(0)
            return f"{m.group(1)}{path}?v={version(target)}{m.group(3)}"

        new = re.sub(r'(<link rel="stylesheet" href=")([^"]+)(")', stamp, html)
        if new != html:
            page.write_text(new, encoding="utf-8")


def parse(page: Path) -> Refs:
    refs = Refs()
    refs.feed(page.read_text(encoding="utf-8"))
    return refs


def main() -> int:
    errors = []
    root = SITE.resolve()
    pages = sorted(SITE.rglob("*.html"))
    if "--fix" in sys.argv[1:]:
        fix_versions(pages)
    parsed = {p.resolve(): parse(p) for p in pages}
    if not (SITE / "index.html").is_file() or not (SITE / "privacy" / "index.html").is_file():
        errors.append("site/index.html and site/privacy/index.html must exist")
    for page in pages:
        refs = parsed[page.resolve()]
        for ref in refs.loads:
            if urlsplit(ref).scheme or ref.startswith("//"):
                errors.append(f"{page}: loads {ref} from another origin")
        for ref in refs.links + refs.loads:
            if ref.startswith(("mailto:", "tel:")):
                continue
            target = local_target(page, ref)
            if target is None:
                continue
            target = target.resolve()
            # Only site/ is uploaded, so anything outside it 404s once deployed.
            if not target.is_relative_to(root) or not target.is_file():
                errors.append(f"{page}: {ref} does not exist in site/")
                continue
            if ref in refs.styles and urlsplit(ref).query != f"v={version(target)}":
                errors.append(f"{page}: {ref} should end ?v={version(target)} (run check_site.py --fix)")
            frag = urlsplit(ref).fragment
            if frag and target in parsed and frag not in parsed[target].ids:
                errors.append(f"{page}: {ref} has no matching id")
    for css in SITE.rglob("*.css"):
        for ref in re.findall(r"""url\(\s*["']?([^"')]+)""", css.read_text(encoding="utf-8")):
            if urlsplit(ref).scheme in ("http", "https") or ref.startswith("//"):
                errors.append(f"{css}: loads {ref} from another origin")
            elif not ref.startswith("data:") and not (
                (css.parent / ref).resolve().is_relative_to(root) and (css.parent / ref).resolve().is_file()
            ):
                errors.append(f"{css}: {ref} does not exist")
        if "@import" in css.read_text(encoding="utf-8"):
            errors.append(f"{css}: no @import")
    for e in errors:
        print(f"::error::{e}")
    print(f"checked {len(pages)} pages: {'ok' if not errors else f'{len(errors)} problem(s)'}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
