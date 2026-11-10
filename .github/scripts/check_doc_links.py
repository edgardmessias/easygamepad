#!/usr/bin/env python3
"""Verify relative Markdown links under docs/ point at existing repository files."""
from __future__ import annotations

import re
import sys
from pathlib import Path

LINK_RE = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


def collect_doc_paths(root: Path) -> list[Path]:
    return sorted(root.glob("docs/**/*.md")) + sorted(root.glob("*.md"))


def link_targets(markdown: str) -> list[str]:
    targets: list[str] = []
    for raw in LINK_RE.findall(markdown):
        target = raw.strip()
        if not target or target.startswith(("http://", "https://", "mailto:")):
            continue
        path_part = target.split("#", 1)[0]
        if not path_part:
            continue
        targets.append(path_part)
    return targets


def check_repository(root: Path) -> list[str]:
    errors: list[str] = []
    for doc in collect_doc_paths(root):
        text = doc.read_text(encoding="utf-8")
        for target in link_targets(text):
            resolved = (doc.parent / target).resolve()
            try:
                resolved.relative_to(root.resolve())
            except ValueError:
                errors.append(f"{doc.relative_to(root)}: link leaves repository: {target}")
                continue
            if not (resolved.is_file() or resolved.is_dir()):
                errors.append(f"{doc.relative_to(root)}: missing target {target}")
    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    errors = check_repository(root)
    for line in errors:
        print(line, file=sys.stderr)
    if errors:
        print(f"{len(errors)} broken relative link(s) in documentation.", file=sys.stderr)
        return 1
    print("Documentation relative links OK.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
