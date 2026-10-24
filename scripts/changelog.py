#!/usr/bin/env python3
"""
Builds the YouTube part of the Morphe patches changelog for a patches version.

Stable version: its own section (it already lists everything since the previous stable).
Dev version: every dev section since the previous stable version, newest first.

Usage: changelog.py CHANGELOG.md VERSION OUT_MARKDOWN OUT_TEXT
"""
import re
import sys

SECTION = re.compile(r"^##\s+\[([^\]]+)\]")
EXCLUDED_SCOPES = ("reddit", "music")


def clean(text):
    text = re.sub(r"\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\)", "", text)       # (commit)
    text = re.sub(r"\s*\(\[#\d+\]\([^)]*\)\)", "", text)                  # (#PR)
    text = re.sub(r",?\s*closes\s+.*$", "", text, flags=re.I)              # closes #123
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)                   # [text](url)
    return text.strip()


def relevant(item):
    m = re.match(r"\*\*([^*]+):\*\*", item)
    if not m:
        return True  # General change.
    scope = m.group(1).lower()
    return not any(word in scope for word in EXCLUDED_SCOPES)


def parse(path):
    sections, current = [], None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        m = SECTION.match(line)
        if m:
            current = {"version": m.group(1), "groups": []}
            sections.append(current)
        elif current is not None and line.startswith("### "):
            current["groups"].append({"title": line[4:].strip(), "items": []})
        elif current is not None and line.startswith("* ") and current["groups"]:
            item = clean(line[2:])
            if relevant(item):
                current["groups"][-1]["items"].append(item)
    return sections


GROUP_ORDER = ("feature", "fix", "app support")


def group_rank(title):
    title = title.lower()
    return next((i for i, word in enumerate(GROUP_ORDER) if word in title), len(GROUP_ORDER))


def select(sections, version):
    index = next((i for i, s in enumerate(sections) if s["version"] == version), None)
    if index is None:
        raise SystemExit(f"Version {version} not found in changelog")
    if "-dev" not in version:
        return [sections[index]]
    selected = []
    for section in sections[index:]:
        if "-dev" not in section["version"]:
            break
        selected.append(section)
    return selected


def main():
    path, version, out_md, out_txt = sys.argv[1:5]
    selected = [s for s in select(parse(path), version.lstrip("v"))
                if any(g["items"] for g in s["groups"])]
    many = len(selected) > 1

    md, txt = [], []
    for section in selected:
        if many:
            md.append(f"### {section['version']}\n")
            txt.append(section["version"])
        # New features first, then bug fixes, then app support.
        for group in sorted(section["groups"], key=lambda g: group_rank(g["title"])):
            if not group["items"]:
                continue
            md.append(f"{'####' if many else '###'} {group['title']}\n")
            md.extend(f"* {item}" for item in group["items"])
            md.append("")
            txt.append(group["title"])
            txt.extend("• " + item.replace("**", "").replace("`", "") for item in group["items"])
            txt.append("")
    open(out_md, "w", encoding="utf-8").write("\n".join(md).strip() + "\n")
    open(out_txt, "w", encoding="utf-8").write("\n".join(txt).strip() + "\n")


if __name__ == "__main__":
    main()
