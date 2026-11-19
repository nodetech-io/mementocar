#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check the store listing in fastlane/metadata/android against Google Play limits.

Each locale directory needs title.txt, short_description.txt and full_description.txt.
Release notes in changelogs/<versionCode>.txt are checked when present.
Usage: python3 scripts/check_listing.py [metadata-dir]
"""
import sys
from pathlib import Path

LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500
SINGLE_LINE = {"title.txt", "short_description.txt"}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8").rstrip("\n")


def check_text(name: str, label: str, text: str, limit: int, errors: list[str]) -> None:
    status = "OK" if len(text) <= limit else "TOO LONG"
    print(f"{status:8} {label:42} {len(text):4}/{limit}")
    if len(text) > limit:
        errors.append(f"{label} has {len(text)} characters (limit {limit})")
    if not text.strip():
        errors.append(f"{label} is empty")
    if name in SINGLE_LINE and "\n" in text:
        errors.append(f"{label} must be a single line")


def check(metadata: Path) -> list[str]:
    errors: list[str] = []
    locales = sorted(p for p in metadata.iterdir() if p.is_dir()) if metadata.is_dir() else []
    if not locales:
        return [f"no locales found in {metadata}"]
    for locale in locales:
        for name, limit in LIMITS.items():
            path = locale / name
            if not path.is_file():
                errors.append(f"{locale.name}/{name} is missing")
                continue
            check_text(name, f"{locale.name}/{name}", read(path), limit, errors)
        for path in sorted((locale / "changelogs").glob("*.txt")):
            if not path.stem.isdigit():
                errors.append(f"{locale.name}/changelogs/{path.name} must be named <versionCode>.txt")
            check_text("changelog", f"{locale.name}/changelogs/{path.name}", read(path), CHANGELOG_LIMIT, errors)
    return errors


def main() -> int:
    default = Path(__file__).resolve().parent.parent / "fastlane/metadata/android"
    errors = check(Path(sys.argv[1]) if len(sys.argv) > 1 else default)
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
