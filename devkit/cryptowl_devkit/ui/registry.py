from __future__ import annotations

"""Item type plugin registry — see docs/items.md.

A type provides display metadata, an icon, a path extension, and a body
editor. The generic ItemPage owns the common chrome (title, commit message,
actions); the sidebar, content pane and menus are generated from the registry.
"""

from dataclasses import dataclass
from typing import Callable

from PySide6.QtWidgets import QPlainTextEdit

from . import icons


class PlainTextEditor(QPlainTextEdit):
    """Body editor for the `plain` type (pure text)."""

    def set_text(self, text: str) -> None:
        self.setPlainText(text)

    def get_text(self) -> str:
        return self.toPlainText()


@dataclass(frozen=True)
class ItemType:
    type_id: str
    display: str
    editor: Callable[[], QPlainTextEdit]
    icon_name: str = "document"
    icon_color: str | None = None
    extension: str = ""
    default_classification: str = "C"
    supports_files: bool = False
    diffable: str = "text"          # text | binary | none

    def make_editor(self) -> QPlainTextEdit:
        return self.editor()

    def icon(self):
        return icons.icon(self.icon_name, self.icon_color or icons.DOCUMENT)


REGISTRY: list[ItemType] = [
    ItemType("plain", "Plain", PlainTextEditor, icon_name="document_text",
             icon_color=icons.PLAIN, extension=".txt"),
]

DEFAULT_TYPE = REGISTRY[0].type_id


def get_type(type_id: str) -> ItemType | None:
    return next((item_type for item_type in REGISTRY
                 if item_type.type_id == type_id), None)
