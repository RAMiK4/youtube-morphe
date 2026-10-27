#!/usr/bin/env python3
"""
Builds the YouTube part of the Morphe patches changelog for a patches version.

Stable version: its own section (it already lists everything since the previous stable).
Dev version: the last three dev sections (itself included) since the previous stable version.

The changes are grouped by type (new features, bug fixes, improvements),
and each group lists the versions newest first:

    ### ✨ New Features
    #### 1.49.0-dev.8
    * ...
    ### 🌟 Mod Features        (Markdown only, from MOD_FEATURES)
    * ...
    ### 🐛 Bug Fixes
    #### 1.49.0-dev.8
    * ...

Within a version, the newest change is listed first, by the date of its commit
(DATES: lines of "<commit hash> <unix time>", from git log). Without dates the order of Morphe is kept.

The Markdown file is the release notes, the text file is the changelog of the in-app update dialog.

Usage: changelog.py CHANGELOG.md VERSION OUT_MARKDOWN OUT_TEXT [--mod-features FILE] [--dates FILE]
"""
import argparse
import re

SECTION = re.compile(r"^##\s+\[([^\]]+)\]")
COMMIT = re.compile(r"\(\[([0-9a-f]{7,40})\]\(")
EXCLUDED_SCOPES = ("reddit", "music")
SKIPPED_GROUPS = ("app support",)
DEV_SECTIONS = 3  # Dev changelogs: the version itself and the two before it.

# Change types in the order they are shown. Older changelogs have titles without emoji.
GROUPS = (
    ("feature", "✨ New Features"),
    ("fix", "🐛 Bug Fixes"),
    ("improvement", "🔧 Improvements"),
)
MOD_FEATURES_TITLE = "🌟 Mod Features"


def clean(text):
    text = re.sub(r"\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\)", "", text)       # (commit)
    text = re.sub(r"\s*\(\[#\d+\]\([^)]*\)\)", "", text)                  # (#PR)
    text = re.sub(r",?\s*closes\s+.*$", "", text, flags=re.I)              # closes #123
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)                   # [text](url)
    return re.sub(r"\s{2,}", " ", text).strip()


def strip_app(item):
    """'**YouTube - Scope:** text' -> '**Scope:** text', '**YouTube:** text' -> 'text'."""
    item = re.sub(r"^\*\*YouTube:\*\*\s*", "", item)
    return re.sub(r"^\*\*YouTube - ", "**", item)


def relevant(item):
    m = re.match(r"\*\*([^*]+):\*\*", item)
    if not m:
        return True  # General change.
    scope = m.group(1).lower()
    return not any(word in scope for word in EXCLUDED_SCOPES)


def group_index(title):
    title = title.lower()
    return next((i for i, (word, _) in enumerate(GROUPS) if word in title), len(GROUPS) - 1)


def parse(path):
    """[{version, groups: {group index: [(item, commit)]}}], newest first."""
    sections, current, group = [], None, None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        m = SECTION.match(line)
        if m:
            current = {"version": m.group(1), "groups": {}}
            sections.append(current)
            group = None
        elif current is not None and line.startswith("### "):
            title = line[4:].strip()
            group = None if any(word in title.lower() for word in SKIPPED_GROUPS) else group_index(title)
        elif current is not None and group is not None and line.startswith("* "):
            commit = COMMIT.findall(line)
            item = clean(line[2:])
            if relevant(item):
                current["groups"].setdefault(group, []).append(
                    (strip_app(item), commit[-1] if commit else None))
    return sections


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
        if section["groups"]:
            selected.append(section)
    return selected[:DEV_SECTIONS]


def read_mod_features(path):
    items = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith(("* ", "- ")):
            items.append(line[2:].strip())
    return items


def read_dates(path):
    dates = {}
    for line in open(path, encoding="utf-8"):
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            dates[parts[0]] = int(parts[1])
    return dates


def newest_first(items, dates):
    """Sorts by commit date, newest first. Items without a known date keep their place at the end."""
    def date(commit):
        if not commit:
            return None
        return next((t for h, t in dates.items() if h.startswith(commit)), None)

    known = [(date(c), i, text) for i, (text, c) in enumerate(items)]
    dated = sorted((x for x in known if x[0] is not None), key=lambda x: (-x[0], x[1]))
    undated = [x for x in known if x[0] is None]
    return [text for _, _, text in dated + undated]


def plain(item):
    return item.replace("**", "").replace("`", "")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("changelog")
    parser.add_argument("version")
    parser.add_argument("out_md")
    parser.add_argument("out_txt")
    parser.add_argument("--mod-features")
    parser.add_argument("--dates")
    args = parser.parse_args()

    mod_features = read_mod_features(args.mod_features) if args.mod_features else []
    dates = read_dates(args.dates) if args.dates else {}
    selected = select(parse(args.changelog), args.version.lstrip("v"))

    md, txt = [], []
    for index, (_, title) in enumerate(GROUPS):
        versions = [(s["version"], newest_first(s["groups"][index], dates))
                    for s in selected if index in s["groups"]]
        if versions:
            md.append(f"### {title}\n")
            txt.append(title)
            for name, items in versions:
                md.append(f"#### {name}\n")
                md.extend(f"* {item}" for item in items)
                md.append("")
                txt.append(f"  {name}")
                txt.extend(f"  • {plain(item)}" for item in items)
                txt.append("")
        # The features of the mod follow the new features of Morphe.
        if index == 0 and mod_features:
            md.append(f"### {MOD_FEATURES_TITLE}\n")
            md.extend(f"* {item}" for item in mod_features)
            md.append("")

    open(args.out_md, "w", encoding="utf-8").write("\n".join(md).strip() + "\n")
    open(args.out_txt, "w", encoding="utf-8").write("\n".join(txt).strip() + "\n")


if __name__ == "__main__":
    main()
