import tempfile
import unittest
from pathlib import Path

from check_doc_links import check_repository


class DocLinkCheckTests(unittest.TestCase):
    def test_flags_missing_relative_target(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "docs" / "a.md").write_text("[missing](../README.md)\n", encoding="utf-8")
            (root / "README.md").write_text("# ok\n", encoding="utf-8")
            (root / "docs" / "b.md").write_text("[gone](nope.md)\n", encoding="utf-8")
            errors = check_repository(root)
            self.assertTrue(any("b.md" in item and "nope.md" in item for item in errors))

    def test_accepts_existing_relative_targets(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "README.md").write_text("# root\n", encoding="utf-8")
            (root / "docs" / "guide.md").write_text("See [root](../README.md).\n", encoding="utf-8")
            self.assertEqual([], check_repository(root))


if __name__ == "__main__":
    unittest.main()
