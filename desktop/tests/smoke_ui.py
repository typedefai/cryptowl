"""Headless UI smoke test: builds every tab against a real temp vault.

    QT_QPA_PLATFORM=offscreen .venv/bin/python tests/smoke_ui.py
"""
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from PyQt6.QtCore import Qt  # noqa: E402
from PyQt6.QtGui import QImage  # noqa: E402
from PyQt6.QtWidgets import QApplication  # noqa: E402

from cryptowl_desktop.ui.main_window import MainWindow  # noqa: E402
from cryptowl_desktop.vault import Vault  # noqa: E402


def main() -> int:
    app = QApplication([])
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "personal")
        Vault.create(path, b"smoke-test-password", vault_id="personal",
                     name="Smoke").close()

        window = MainWindow()
        window._unlock(path, "smoke-test-password")
        tabs = window._tabs
        assert tabs is not None, "tabs were not built"
        names = [tabs.tabText(i) for i in range(tabs.count())]
        assert names == ["Notes", "Media", "Moments", "Passwords", "Debug"], names

        notes = tabs.widget(0)
        notes.title_edit.setText("Smoke note")
        notes.content_edit.setPlainText("# Hello")
        notes._save()
        assert len(notes.repo.list()) == 1

        media = tabs.widget(1)
        png = os.path.join(tmp, "tiny.png")
        image = QImage(8, 8, QImage.Format.Format_ARGB32)
        image.fill(Qt.GlobalColor.magenta)
        assert image.save(png, "PNG")
        media._import_one(png)
        media.refresh()
        assert media.list_widget.count() == 1, media.list_widget.count()

        debug = tabs.widget(4)
        debug.refresh()
        assert debug.tree.topLevelItemCount() > 0

        window.lock()
        print("UI smoke OK:", names)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
