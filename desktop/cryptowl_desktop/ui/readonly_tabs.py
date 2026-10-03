from __future__ import annotations

"""Read-only tabs: Moments timeline, Passwords (locked on desktop), Debug."""

import datetime as _dt
import json

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QGuiApplication
from PyQt6.QtWidgets import (QHBoxLayout, QLabel, QListWidget, QListWidgetItem,
                             QPushButton, QSplitter, QTableWidget,
                             QTableWidgetItem, QTextBrowser, QTreeWidget,
                             QTreeWidgetItem, QVBoxLayout, QWidget)

from ..vault import MomentsRepository, PasswordRepository
from ..vault import debugtools


def _notify_properties(owner):
    callback = getattr(owner, "on_properties_changed", None)
    if callback is not None:
        callback()


def _fmt_time(ms) -> str:
    if not ms:
        return ""
    try:
        return _dt.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")
    except (OverflowError, OSError, ValueError):
        return str(ms)


class MomentsTab(QWidget):
    """The imported timeline, read-only (writing needs the Android compose UI)."""

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.repo = MomentsRepository(vault.conn)
        self._posts = []

        self.list_widget = QListWidget()
        self.list_widget.currentRowChanged.connect(self._show)
        self.detail = QTextBrowser()

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(self.list_widget)
        splitter.addWidget(self.detail)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 2)

        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.refresh)

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("Moments timeline (Confidential tier, imported archive)"))
        layout.addWidget(refresh, alignment=Qt.AlignmentFlag.AlignLeft)
        layout.addWidget(splitter, 1)
        self.refresh()

    def properties(self):
        row = self.list_widget.currentRow()
        if row < 0 or row >= len(self._posts):
            return [("State", "no moment selected")]
        post = self._posts[row]
        return [
            ("Moment id", post["id"]),
            ("Type", post["type"]),
            ("Visibility", post["visibility"]),
            ("Time", _fmt_time(post["source_created_at"])),
            ("Media", str(len(post["media"]))),
            ("Comments", str(post["comment_count"])),
            ("Likes", str(post["like_count"])),
        ]

    def refresh(self):
        self._posts = self.repo.timeline()
        self.list_widget.clear()
        for post in self._posts:
            author = post["author_name"] or post["author_username"] or "?"
            text = (post["content"] or "").replace("\n", " ")[:80]
            label = f"{_fmt_time(post['source_created_at'])}  {author}\n{text}"
            item = QListWidgetItem(label)
            item.setToolTip(f"id={post['id']}\nvisibility={post['visibility']}")
            self.list_widget.addItem(item)
        if self._posts:
            self.list_widget.setCurrentRow(0)

    def _show(self, row: int):
        if row < 0 or row >= len(self._posts):
            self.detail.clear()
            return
        post = self._posts[row]
        lines = [
            f"<b>{post['author_name'] or post['author_username'] or '?'}</b>"
            f" · {_fmt_time(post['source_created_at'])}"
            f" · <i>{post['visibility']}</i> · type={post['type']}",
            "",
        ]
        if post["content"]:
            lines.append(post["content"].replace("\n", "<br>"))
        if post["location"]:
            lines.append(f"<i>location: {post['location']}</i>")
        if post["media"]:
            lines.append("<br><b>Media</b>")
            for media in post["media"]:
                lines.append(f"• {media['media_type']} — {media['original_name'] or media['filename']}"
                             f" ({media['width']}x{media['height']})")
        if post["comments"]:
            lines.append("<br><b>Comments</b>")
            for comment in post["comments"]:
                who = comment["author_name"] or comment["author_username"] or "?"
                lines.append(f"• <b>{who}</b>: {comment['content'] or ''}")
        if post["likes"]:
            names = ", ".join(like["author_name"] or like["author_username"] or "?"
                              for like in post["likes"])
            lines.append(f"<br>❤ {names}")
        self.detail.setHtml("<br>".join(lines))
        _notify_properties(self)


class PasswordsTab(QWidget):
    """Password metadata is readable; S/T payloads are Android-gated."""

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.repo = PasswordRepository(vault.conn)

        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(
            ["Title", "Class", "Updated", "Encrypted data id"])
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.currentCellChanged.connect(lambda *_: self._explain())

        self.explain = QLabel()
        self.explain.setWordWrap(True)

        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.refresh)

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel(
            "Password entries are Secret tier. The list (titles) is L0 metadata; "
            "usernames/passwords/notes live in t_encrypted_data and are encrypted "
            "with a per-item DEK wrapped by the KEK, which only exists inside the "
            "Android Keystore behind a fingerprint. They cannot be decrypted here."))
        layout.addWidget(refresh, alignment=Qt.AlignmentFlag.AlignLeft)
        layout.addWidget(self.table, 1)
        layout.addWidget(self.explain)
        self.refresh()

    def properties(self):
        row = self.table.currentRow()
        if row < 0:
            return [("State", "no entry selected")]
        return [
            ("Title", self.table.item(row, 0).text() if self.table.item(row, 0) else ""),
            ("Class", self.table.item(row, 1).text() if self.table.item(row, 1) else ""),
            ("Updated", self.table.item(row, 2).text() if self.table.item(row, 2) else ""),
            ("Encrypted data id",
             self.table.item(row, 3).text() if self.table.item(row, 3) else ""),
            ("Decryptable here", "no — Android fingerprint (KEK)"),
        ]

    def refresh(self):
        entries = self.repo.list()
        self.table.setRowCount(len(entries))
        for row, entry in enumerate(entries):
            self.table.setItem(row, 0, QTableWidgetItem(entry["title"] or "(untitled)"))
            self.table.setItem(row, 1, QTableWidgetItem(entry["classification"]))
            self.table.setItem(row, 2, QTableWidgetItem(_fmt_time(entry["updated_at"])))
            self.table.setItem(row, 3, QTableWidgetItem(entry["encrypted_data_id"] or ""))
        self._explain()

    def _explain(self):
        row = self.table.currentRow()
        if row < 0:
            self.explain.setText("Select an entry for details.")
            return
        encrypted_id = self.table.item(row, 3).text() if self.table.item(row, 3) else ""
        self.explain.setText(
            f"Selected entry: encrypted_data_id={encrypted_id}. Content requires the "
            "Android fingerprint (KEK → DEK); use the Android app to view or edit it.")
        _notify_properties(self)


class DebugTab(QWidget):
    """Vault internals: meta, key fingerprints, schema, files, table counts."""

    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.vault = vault

        self.tree = QTreeWidget()
        self.tree.setColumnCount(3)
        self.tree.setHeaderLabels(["Section", "Item", "Value"])
        self.tree.setColumnWidth(0, 140)
        self.tree.setColumnWidth(1, 220)

        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.refresh)
        copy_btn = QPushButton("Copy report")
        copy_btn.clicked.connect(self._copy_report)
        raw_btn = QPushButton("Show vault.meta JSON")
        raw_btn.clicked.connect(self._show_meta)

        toolbar = QHBoxLayout()
        for button in (refresh, copy_btn, raw_btn):
            toolbar.addWidget(button)
        toolbar.addStretch(1)

        layout = QVBoxLayout(self)
        layout.addLayout(toolbar)
        layout.addWidget(self.tree, 1)
        self.refresh()

    def properties(self):
        rows = debugtools.vault_report(self.vault)
        return [(label, value) for _section, label, value in rows[:10]]

    def refresh(self):
        self.tree.clear()
        sections = {}
        for section, label, value in debugtools.vault_report(self.vault):
            sections.setdefault(section, []).append((label, value))
        for label, value in debugtools.file_stats(self.vault):
            sections.setdefault("Files", []).append((label, value))
        for name, count in debugtools.table_stats(self.vault):
            sections.setdefault("Tables", []).append((name, str(count)))

        for section, entries in sections.items():
            top = QTreeWidgetItem([section, "", ""])
            top.setExpanded(True)
            for label, value in entries:
                top.addChild(QTreeWidgetItem(["", str(label), str(value)]))
            self.tree.addTopLevelItem(top)
        _notify_properties(self)

    def _copy_report(self):
        lines = []
        for i in range(self.tree.topLevelItemCount()):
            section = self.tree.topLevelItem(i)
            lines.append(f"[{section.text(0)}]")
            for j in range(section.childCount()):
                child = section.child(j)
                lines.append(f"  {child.text(1)} = {child.text(2)}")
        QGuiApplication.clipboard().setText("\n".join(lines))

    def _show_meta(self):
        dialog = QWidget(self, Qt.WindowType.Window)
        dialog.setWindowTitle("vault.meta (mac excluded) — read-only")
        dialog.resize(720, 560)
        view = QTextBrowser()
        meta = dict(self.vault.meta)
        view.setPlainText(json.dumps(meta, indent=2, sort_keys=True))
        layout = QVBoxLayout(dialog)
        layout.addWidget(view)
        dialog.show()
        self._meta_dialog = dialog  # keep a reference
