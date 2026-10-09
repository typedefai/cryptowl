from __future__ import annotations

"""Windows-style icons: Microsoft Fluent UI System Icons (MIT), vendored as
SVGs under assets/icons/ and tinted at load time (folders Explorer-yellow).

These are the Windows 11 icon set (the exact Explorer artwork is proprietary);
see assets/icons/LICENSE.md for the license.
"""

from functools import lru_cache
from importlib.resources import files

from PySide6.QtCore import QByteArray, Qt
from PySide6.QtGui import QIcon, QPainter, QPixmap
from PySide6.QtSvg import QSvgRenderer

SOURCE_COLOR = "#212121"
ACCENT = "#0F6CBD"       # Windows 11 accent blue
FOLDER = "#FFB900"       # Explorer folder yellow
DOCUMENT = "#5B6670"
PLAIN = "#2B88D8"


@lru_cache(maxsize=None)
def icon(name: str, color: str | None = None) -> QIcon:
    data = files("cryptowl_devkit").joinpath(
        "assets", "icons", f"{name}.svg").read_text(encoding="utf-8")
    if color and color.lower() != SOURCE_COLOR:
        data = data.replace(SOURCE_COLOR, color)
    renderer = QSvgRenderer(QByteArray(data.encode("utf-8")))
    pixmap = QPixmap(64, 64)
    pixmap.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pixmap)
    renderer.render(painter)
    painter.end()
    return QIcon(pixmap)


def folder_icon() -> QIcon:
    return icon("folder_filled", FOLDER)
