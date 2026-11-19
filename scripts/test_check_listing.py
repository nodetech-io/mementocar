# SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
# SPDX-License-Identifier: GPL-3.0-or-later

import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

from check_listing import check


class CheckListingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def locale(self, name="es-ES", title="App", short="Short", full="Full\ntext", changelogs=None):
        d = self.root / name
        d.mkdir()
        for file, text in (("title.txt", title), ("short_description.txt", short), ("full_description.txt", full)):
            if text is not None:
                (d / file).write_text(text + "\n", encoding="utf-8")
        for file, text in (changelogs or {}).items():
            (d / "changelogs").mkdir(exist_ok=True)
            (d / "changelogs" / file).write_text(text + "\n", encoding="utf-8")

    def run_check(self, path=None):
        with redirect_stdout(StringIO()):
            return check(path or self.root)

    def test_valid_listing_passes(self):
        self.locale(changelogs={"3.txt": "Fixes"})
        self.assertEqual([], self.run_check())

    def test_limits_are_inclusive(self):
        self.locale(title="x" * 30, short="x" * 80, full="x" * 4000, changelogs={"3.txt": "x" * 500})
        self.assertEqual([], self.run_check())

    def test_too_long_fields_fail(self):
        self.locale(title="x" * 31, short="x" * 81, full="x" * 4001, changelogs={"3.txt": "x" * 501})
        self.assertEqual(4, len(self.run_check()))

    def test_short_fields_must_be_single_line(self):
        self.locale(short="a\nb")
        self.assertIn("es-ES/short_description.txt must be a single line", self.run_check())

    def test_missing_file_fails(self):
        self.locale(full=None)
        self.assertIn("es-ES/full_description.txt is missing", self.run_check())

    def test_changelog_must_be_named_by_version_code(self):
        self.locale(changelogs={"v0.2.1.txt": "Fixes"})
        self.assertIn("es-ES/changelogs/v0.2.1.txt must be named <versionCode>.txt", self.run_check())

    def test_project_listing_is_within_limits(self):
        path = Path(__file__).resolve().parent.parent / "fastlane/metadata/android"
        self.assertEqual([], self.run_check(path))


if __name__ == "__main__":
    unittest.main()
