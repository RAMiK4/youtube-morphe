#!/usr/bin/env python3
"""
Builds the YouTube part of the Morphe patches changelog for a patches version.

Stable version: the new features of its own section (it already lists everything since the previous stable).
Dev version: the new features of all dev versions since the last released one (RELEASED).

Bug fixes and improvements are not listed: the notes always end with a closing heading instead,
and the release notes with the install note under it.
Each group is a title followed by its changes, without version headings:

    ✨ New Features
    • ...
    🌟 Mod Features     (from MOD_FEATURES)
    • ...
    🔧 Bug fixes and other improvements
    ⓘ After downloading, ...   (release notes only)

The in-app dialog shows the titles bold with a thin rule under them. The release notes
use "## <title>" headings (GitHub draws the same kind of rule) and add the files table.

Newer versions come first; within a version the order of the Morphe changelog is kept.

RELEASED: patches versions that already have a release of the mod, one per line or separated by spaces.

EXTRA_FEATURES: same format, listed after the Morphe new features of that version. Only for releases that
skip versions (for example the first dev build after a stable one): important features of the skipped
versions that the selected sections do not list. Not a general rule; most versions have no section.

The Markdown file is the release notes, the text file is the changelog of the in-app update dialog.

Usage: changelog.py CHANGELOG.md VERSION OUT_MARKDOWN OUT_TEXT [--mod-features FILE]
                    [--extra-features FILE] [--released FILE]
"""
import argparse
import re

SECTION = re.compile(r"^##\s+\[([^\]]+)\]")
EXCLUDED_SCOPES = ("reddit", "music")
SKIPPED_GROUPS = ("app support",)

# Change types of the Morphe changelog. Older changelogs have titles without emoji.
# Only the new features are listed.
GROUPS = (
    ("feature", "✨ New Features"),
    ("fix", "🐛 Bug Fixes"),
    ("improvement", "🔧 Improvements"),
)
FEATURE_GROUP = 0
MOD_FEATURES_TITLE = "🌟 Mod Features"
CLOSING_TITLE = "🔧 Bug fixes and other improvements"
# Release notes only: the APK downloaded from the release page is installed by hand.
INSTALL_NOTE = "ⓘ After downloading, don't forget to install the new version from the Downloads folder"


def clean(text):
    text = re.sub(r"\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\)", "", text)       # (commit)
    text = re.sub(r"\s*\(\[#\d+\]\([^)]*\)\)", "", text)                  # (#PR)
    text = re.sub(r",?\s*closes\s+.*$", "", text, flags=re.I)              # closes #123
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)                   # [text](url)
    return re.sub(r"\s{2,}", " ", text).strip()


def strip_app(item):
    """'**YouTube - Scope:** text' -> '**Scope:** text', '**YouTube:** text' -> 'text'."""
    # Shared scopes such as "YouTube / YouTube Music - Scope" or "YouTube & YouTube Music - Scope"
    # are shortened the same way
    item = re.sub(r"^\*\*YouTube(?:\s*[/&]\s*[^*:-]+?)?:\*\*\s*", "", item)
    return re.sub(r"^\*\*YouTube(?:\s*[/&]\s*[^*:-]+?)? - ", "**", item)


def relevant(item):
    m = re.match(r"\*\*([^*]+):\*\*", item)
    if not m:
        return True  # General change.
    scope = m.group(1).lower()
    # "YouTube / YouTube Music - Scope" or "YouTube & YouTube Music - Scope": the apps before " - ";
    # YouTube among them keeps the change
    apps = [app.strip() for app in re.split(r"[/&]", scope.split(" - ")[0])]
    if "youtube" in apps:
        return True
    return not any(word in scope for word in EXCLUDED_SCOPES)


def group_index(title):
    title = title.lower()
    return next((i for i, (word, _) in enumerate(GROUPS) if word in title), len(GROUPS) - 1)


def parse(path):
    """[{version, groups: {group index: [item]}}], newest first, items in the order of Morphe."""
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
            item = clean(line[2:])
            if relevant(item):
                current["groups"].setdefault(group, []).append(strip_app(item))
    return sections


def select(sections, version, released=()):
    index = next((i for i, s in enumerate(sections) if s["version"] == version), None)
    if index is None:
        raise SystemExit(f"Version {version} not found in changelog")
    if "-dev" not in version:
        return [sections[index]]
    selected = []
    for section in sections[index:]:
        if "-dev" not in section["version"]:
            break
        # A dev that was already released listed its own features
        if section["version"] != version and section["version"] in released:
            break
        if section["groups"]:
            selected.append(section)
    return selected


def read_mod_features(path):
    items = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith(("* ", "- ")):
            items.append(line[2:].strip())
    return items


def read_version_items(path, version):
    items, current = [], None
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith("## "):
            current = line[3:].strip().lstrip("v")
        elif current == version and line.startswith(("* ", "- ")):
            items.append(line[2:].strip())
    return items


def read_released(path):
    return {v.lstrip("v") for v in open(path, encoding="utf-8").read().split()}


def plain(item):
    return item.replace("**", "").replace("`", "")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("changelog")
    parser.add_argument("version")
    parser.add_argument("out_md")
    parser.add_argument("out_txt")
    parser.add_argument("--mod-features")
    parser.add_argument("--extra-features")
    parser.add_argument("--released")
    args = parser.parse_args()

    version = args.version.lstrip("v")
    released = read_released(args.released) if args.released else set()
    selected = select(parse(args.changelog), version, released)
    extra_features = read_version_items(args.extra_features, version) if args.extra_features else []
    mod_features = read_mod_features(args.mod_features) if args.mod_features else []

    groups = []
    features = [item for s in selected for item in s["groups"].get(FEATURE_GROUP, [])] + extra_features
    if features:
        groups.append((GROUPS[FEATURE_GROUP][1], features))
    # The features of the mod follow the new features of Morphe.
    if mod_features:
        groups.append((MOD_FEATURES_TITLE, mod_features))

    md, txt = [], []
    for title, items in groups:
        # Markdown: "##" headings get GitHub's thin rule under them.
        md += [f"## {title}", ""]
        md += [f"* {item}" for item in items]
        md.append("")
        # The app draws a thin rule under each title.
        txt += [title]
        txt += [f"• {plain(item)}" for item in items]
        txt.append("")

    # Fixes are not listed one by one; a closing heading ends the notes.
    md += [f"## {CLOSING_TITLE}", "", INSTALL_NOTE]
    txt.append(CLOSING_TITLE)

    open(args.out_md, "w", encoding="utf-8").write("\n".join(md).strip() + "\n")
    open(args.out_txt, "w", encoding="utf-8").write("\n".join(txt).strip() + "\n")


if __name__ == "__main__":
    main()
