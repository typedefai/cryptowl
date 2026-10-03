"""Headless UI smoke test: builds every editor against a real temp vault.

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
from cryptowl_desktop.ui.navigator import (FEATURE, FILES, LAB, OVERVIEW, SQL,
                                           TABLE)
from cryptowl_desktop.vault import Vault  # noqa: E402


def main() -> int:
    _app = QApplication([])  # noqa: F841 - keeps Qt alive
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "personal")
        Vault.create(path, b"smoke-test-password", vault_id="personal",
                     name="Smoke").close()

        window = MainWindow()
        window._unlock(path, "smoke-test-password")
        assert window.editors.count() >= 1, "overview did not open"

        # every navigator node opens its editor
        for kind, key in ((TABLE, "t_note"), (TABLE, "t_password"),
                          (FILES, "attachments"), (FILES, "thumbnails"),
                          (SQL, "sql"), (LAB, "lab"), (OVERVIEW, "overview"),
                          (FEATURE, "notes"), (FEATURE, "media"),
                          (FEATURE, "moments"), (FEATURE, "passwords")):
            window.open_editor(kind, key)
        assert window.editors.count() == 11, window.editors.count()

        # table editor: model, filter, sort, FK nav
        notes_editor = window._editors["table:t_note"]
        notes_editor.filter_edit.setText("title = Hello")
        notes_editor._apply_filter()
        assert notes_editor.model.rowCount() == 0  # no notes yet
        notes_editor._clear_filter()
        window._fk_open("t_encrypted_data", "id", "00000000-0000-0000-0000-000000000000")
        assert "table:t_encrypted_data" in window._editors

        # sql console: run a select headlessly
        console = window._editors["sql:sql"]
        console.editor.setPlainText("SELECT count(*) AS n FROM t_note;")
        console.execute()
        assert "ERROR" not in console.messages.toPlainText(), console.messages.toPlainText()
        assert console.results.rowCount() == 1

        # crypto lab: populated + a wrong-key unwrap fails without a dialog
        lab = window._editors["lab:lab"]
        assert lab.key_pick.count() >= 1, "no wrapped keys listed"
        lab.key_pick.setCurrentIndex(0)
        lab.wrapping_key_edit.setText("00" * 32)
        lab._unwrap()
        assert lab.result1.text().startswith("unwrap FAILED"), lab.result1.text()

        # media: import a tiny png through the friendly editor
        media = window._editors["feature:media"]
        png = os.path.join(tmp, "tiny.png")
        image = QImage(8, 8, QImage.Format.Format_ARGB32)
        image.fill(Qt.GlobalColor.magenta)
        assert image.save(png, "PNG")
        media._import_one(png)
        media.refresh()
        assert media.list_widget.count() == 1, media.list_widget.count()

        # value panel: inspect a cell of t_file via hook
        window._value_selected("t_file", "storage_name", {"id": "x", "storage_name": "x.cwo"})
        assert window.value_panel._cell is not None

        window.lock()
        assert window.vault is None
        print("UI smoke OK: 11 editors + value panel + console + lab")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
