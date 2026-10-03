"""Recent-vaults store (pure Python, no Qt needed)."""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from cryptowl_desktop.ui.recent import RecentVaults  # noqa: E402


class RecentVaultsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.store = os.path.join(self._tmp.name, "recent.json")
        self.a = os.path.join(self._tmp.name, "personal")
        self.b = os.path.join(self._tmp.name, "work")

    def tearDown(self):
        self._tmp.cleanup()

    def test_add_dedupe_order_and_persist(self):
        recent = RecentVaults(self.store)
        recent.add(self.a, "Personal")
        recent.add(self.b, "Work")
        self.assertEqual([self.b, self.a], [e["path"] for e in recent.entries()])

        recent.add(self.a, "Personal 2")  # re-open moves to front, renames
        self.assertEqual([self.a, self.b], [e["path"] for e in recent.entries()])
        self.assertEqual("Personal 2", recent.entries()[0]["name"])

        reloaded = RecentVaults(self.store)
        self.assertEqual([self.a, self.b], [e["path"] for e in reloaded.entries()])
        self.assertEqual(self.a, reloaded.latest_path())

    def test_remove_and_clear(self):
        recent = RecentVaults(self.store)
        recent.add(self.a)
        recent.add(self.b)
        recent.remove(self.a)
        self.assertEqual([self.b], [e["path"] for e in recent.entries()])
        recent.clear()
        self.assertEqual([], RecentVaults(self.store).entries())

    def test_truncates_to_max(self):
        recent = RecentVaults(self.store)
        for index in range(RecentVaults.MAX + 3):
            recent.add(os.path.join(self._tmp.name, f"vault-{index}"))
        entries = recent.entries()
        self.assertEqual(RecentVaults.MAX, len(entries))
        self.assertTrue(entries[0]["path"].endswith(
            f"vault-{RecentVaults.MAX + 2}"))

    def test_corrupt_file_is_ignored(self):
        with open(self.store, "w", encoding="utf-8") as f:
            f.write("{not json")
        self.assertEqual([], RecentVaults(self.store).entries())


if __name__ == "__main__":
    unittest.main()
