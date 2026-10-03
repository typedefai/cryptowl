from __future__ import annotations

"""Notes tab — Confidential-tier note CRUD (plaintext inside SQLCipher)."""

import datetime as _dt

from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import (QCheckBox, QHBoxLayout, QLabel, QLineEdit,
                             QListWidget, QListWidgetItem, QMessageBox,
                             QPlainTextEdit, QPushButton, QSplitter,
                             QStackedWidget, QTextBrowser, QVBoxLayout,
                             QWidget)

from ..vault import NoteRepository


class NotesTab(QWidget):
    def __init__(self, vault, parent=None):
        super().__init__(parent)
        self.repo = NoteRepository(vault.conn)
        self.current_id = None
        self._loading = False

        self.list_widget = QListWidget()
        self.list_widget.currentItemChanged.connect(self._on_selected)

        self.title_edit = QLineEdit()
        self.title_edit.setPlaceholderText("Title")
        self.content_edit = QPlainTextEdit()
        self.content_edit.setPlaceholderText("Write markdown… (# headings, **bold**, lists)")
        self.preview = QTextBrowser()
        self.preview.setOpenExternalLinks(False)
        self.stack = QStackedWidget()
        self.stack.addWidget(self.content_edit)
        self.stack.addWidget(self.preview)

        self.preview_check = QCheckBox("Preview")
        self.preview_check.toggled.connect(self._render_preview)
        self.pinned_check = QCheckBox("Pinned")

        new_btn = QPushButton("New")
        save_btn = QPushButton("Save")
        delete_btn = QPushButton("Delete")
        new_btn.clicked.connect(self._new)
        save_btn.clicked.connect(self._save)
        delete_btn.clicked.connect(self._delete)

        buttons = QHBoxLayout()
        buttons.addWidget(new_btn)
        buttons.addWidget(save_btn)
        buttons.addWidget(delete_btn)
        buttons.addStretch(1)
        buttons.addWidget(self.pinned_check)
        buttons.addWidget(self.preview_check)

        right = QVBoxLayout()
        right.addWidget(QLabel("Edit / preview"))
        right.addWidget(self.title_edit)
        right.addWidget(self.stack, 1)
        right.addLayout(buttons)

        right_widget = QWidget()
        right_widget.setLayout(right)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(self.list_widget)
        splitter.addWidget(right_widget)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 2)

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel(
            "Notes are Confidential tier: stored in SQLCipher, readable whenever "
            "the vault is unlocked."))
        layout.addWidget(splitter)

        self.refresh()

    def _notify_properties(self):
        callback = getattr(self, "on_properties_changed", None)
        if callback is not None:
            callback()

    def properties(self):
        if self.current_id is None:
            return [("State", "no note selected")]
        note = self.repo.get(self.current_id)
        if note is None:
            return [("State", "note not found")]
        return [
            ("Note id", note["id"]),
            ("Title", note["title"] or "(untitled)"),
            ("Pinned", "yes" if note["pinned"] else "no"),
            ("Content", f"{len(note['content'])} chars"),
            ("Updated", _fmt_time(note["updated_at"])),
        ]

    # ------------------------------------------------------------------ state

    def refresh(self) -> None:
        self._loading = True
        self.list_widget.clear()
        for note in self.repo.list():
            label = ("★ " if note["pinned"] else "") + (note["title"] or "(untitled)")
            item = QListWidgetItem(label)
            item.setData(Qt.ItemDataRole.UserRole, note["id"])
            self.list_widget.addItem(item)
        self._loading = False
        if self.list_widget.count():
            self.list_widget.setCurrentRow(0)
        else:
            self._clear_editor()

    def _clear_editor(self):
        self.current_id = None
        self.title_edit.clear()
        self.content_edit.clear()
        self.pinned_check.setChecked(False)

    def _on_selected(self, current, _previous):
        if self._loading or current is None:
            return
        note = self.repo.get(current.data(Qt.ItemDataRole.UserRole))
        if note is None:
            return
        self.current_id = note["id"]
        self.title_edit.setText(note["title"])
        self.content_edit.setPlainText(note["content"])
        self.pinned_check.setChecked(note["pinned"])
        self._render_preview()
        self._notify_properties()

    def _new(self):
        self.list_widget.setCurrentItem(None)
        self._clear_editor()
        self.title_edit.setFocus()

    def _save(self):
        title = self.title_edit.text()
        content = self.content_edit.toPlainText()
        pinned = self.pinned_check.isChecked()
        if self.current_id is None:
            self.current_id = self.repo.create(title, content, pinned)
        else:
            self.repo.update(self.current_id, title, content, pinned)
        selected = self.current_id
        self.refresh()
        for row in range(self.list_widget.count()):
            item = self.list_widget.item(row)
            if item.data(Qt.ItemDataRole.UserRole) == selected:
                self.list_widget.setCurrentRow(row)
                break

    def _delete(self):
        if self.current_id is None:
            return
        if QMessageBox.question(
                self, "Delete note", "Delete this note from the vault?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        ) != QMessageBox.StandardButton.Yes:
            return
        self.repo.soft_delete(self.current_id)
        self._clear_editor()
        self.refresh()

    def _render_preview(self, *_):
        self.stack.setCurrentIndex(1 if self.preview_check.isChecked() else 0)
        if self.preview_check.isChecked():
            self.preview.setMarkdown(self.content_edit.toPlainText())


def _fmt_time(ms) -> str:
    try:
        return _dt.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")
    except (OverflowError, OSError, ValueError, TypeError):
        return str(ms)
