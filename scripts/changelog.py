#!/usr/bin/env python3
"""
Builds the YouTube part of the Morphe patches changelog for a patches version.

Stable version: its own section (it already lists everything since the previous stable).
Dev version: new features of the last two dev sections (itself included),
bug fixes and improvements of the version itself only.

Each group title stands between two separator lines, followed by its changes, without version headings:

    ────────────────
    ✨ New Features
    ────────────────
    • ...
    ────────────────
    🌟 Mod Features     (from MOD_FEATURES)
    ────────────────
    • ...
    ────────────────
    🐛 Bug Fixes
    ────────────────
    • ...              (Morphe fixes, then the fixes of the mod from MOD_FIXES)

The release notes add the files table after them under the same kind of title.

Newer versions come first; within a version the newest change is listed first, by the date of its commit
(DATES: lines of "<commit hash> <unix time>", from git log). Without dates the order of Morphe is kept.

MOD_FIXES: "## <patches version>" headings with "* " items, listed after the Morphe fixes of that version.

The Markdown file is the release notes, the text file is the changelog of the in-app update dialog.

Usage: changelog.py CHANGELOG.md VERSION OUT_MARKDOWN OUT_TEXT [--mod-features FILE] [--mod-fixes FILE] [--dates FILE]
"""
import argparse
import re

SECTION = re.compile(r"^##\s+\[([^\]]+)\]")
COMMIT = re.compile(r"\(\[([0-9a-f]{7,40})\]\(")
EXCLUDED_SCOPES = ("reddit", "music")
SKIPPED_GROUPS = ("app support",)
DEV_FEATURE_SECTIONS = 2  # Dev changelogs: new features of the version itself and the one before it.
SEPARATOR = "─" * 16

# Change types in the order they are shown. Older changelogs have titles without emoji.
GROUPS = (
    ("feature", "✨ New Features"),
    ("fix", "🐛 Bug Fixes"),
    ("improvement", "🔧 Improvements"),
)
FIX_GROUP = 1
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
    return selected[:DEV_FEATURE_SECTIONS]


def read_mod_features(path):
    items = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith(("* ", "- ")):
            items.append(line[2:].strip())
    return items


def read_mod_fixes(path, version):
    items, current = [], None
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith("## "):
            current = line[3:].strip().lstrip("v")
        elif current == version and line.startswith(("* ", "- ")):
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
    parser.add_argument("--mod-fixes")
    parser.add_argument("--dates")
    args = parser.parse_args()

    version = args.version.lstrip("v")
    dates = read_dates(args.dates) if args.dates else {}
    selected = select(parse(args.changelog), version)
    mod_fixes = read_mod_fixes(args.mod_fixes, version) if args.mod_fixes else []
    mod_features = read_mod_features(args.mod_features) if args.mod_features else []

    groups = []
    for index, (_, title) in enumerate(GROUPS):
        # New features: all selected versions. Fixes and improvements: the version itself only.
        sections = selected if index == 0 else [s for s in selected if s["version"] == version]
        items = [item for s in sections if index in s["groups"]
                 for item in newest_first(s["groups"][index], dates)]
        if index == FIX_GROUP:
            items += mod_fixes
        if items:
            groups.append((title, items))
        # The features of the mod follow the new features of Morphe.
        if index == 0 and mod_features:
            groups.append((MOD_FEATURES_TITLE, mod_features))

    md, txt = [], []
    for title, items in groups:
        # Markdown: blank lines keep the separators out of the list and the heading.
        md += [SEPARATOR, "", f"### {title}", "", SEPARATOR, ""]
        md += [f"* {item}" for item in items]
        md.append("")
        txt += [SEPARATOR, title, SEPARATOR]
        txt += [f"• {plain(item)}" for item in items]

    open(args.out_md, "w", encoding="utf-8").write("\n".join(md).strip() + "\n")
    open(args.out_txt, "w", encoding="utf-8").write("\n".join(txt).strip() + "\n")


if __name__ == "__main__":
    main()
