from __future__ import annotations

"""Visual Studio 2010-inspired Qt theme (silver chrome + blue accents).

Applied on top of the Fusion style so the app looks consistent on macOS,
Windows and Linux.
"""

from PyQt6.QtGui import QColor, QFont, QPalette
from PyQt6.QtWidgets import QApplication

# Classic VS2010 shell palette
WINDOW = "#EEEEF2"
PANEL = "#F0F0F0"
BORDER = "#A0A0A0"
BORDER_SOFT = "#C5C5C5"
TEXT = "#1E1E1E"
MUTED = "#5A5A5A"
SELECT_BG = "#3399FF"
SELECT_FG = "#FFFFFF"
ACCENT = "#007ACC"
CAPTION_TOP = "#EAF3FB"
CAPTION_BOTTOM = "#CFE3F7"
CAPTION_TEXT = "#1E395B"
TOOLBAR_TOP = "#F5F5F5"
TOOLBAR_BOTTOM = "#E4E4E4"
STATUS_TOP = "#3E7CB8"
STATUS_BOTTOM = "#2E5F8A"

VS2010_QSS = f"""
/* No explicit UI font: use the platform default (an explicit missing family
   such as "Segoe UI" makes Qt populate alias tables at startup — slow + noisy). */
QWidget {{
    font-size: 12px;
    color: {TEXT};
}}
QMainWindow, QDialog {{ background: {WINDOW}; }}

/* ---------------------------------------------------------------- menu bar */
QMenuBar {{
    background: {WINDOW};
    border-bottom: 1px solid {BORDER_SOFT};
    padding: 1px 2px;
}}
QMenuBar::item {{ padding: 3px 9px; background: transparent; }}
QMenuBar::item:selected {{ background: #CDE6FF; border: 1px solid #99CCFF; }}
QMenu {{
    background: #F7F7F7;
    border: 1px solid #8E8E8E;
    padding: 2px;
}}
QMenu::item {{ padding: 4px 24px 4px 24px; }}
QMenu::item:selected {{ background: #CDE6FF; border: 1px solid #99CCFF; }}
QMenu::separator {{ height: 1px; background: #D5D5D5; margin: 3px 6px; }}

/* ---------------------------------------------------------------- toolbar */
QToolBar {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                stop:0 {TOOLBAR_TOP}, stop:1 {TOOLBAR_BOTTOM});
    border-bottom: 1px solid {BORDER_SOFT};
    spacing: 1px;
    padding: 2px 4px;
}}
QToolBar::separator {{ width: 1px; background: #C9C9C9; margin: 3px 4px; }}
QToolButton {{
    border: 1px solid transparent;
    padding: 3px 5px;
    border-radius: 1px;
}}
QToolButton:hover {{ border: 1px solid #A5C6E8; background: #D6E8FA; }}
QToolButton:pressed {{ background: #BBD9F5; }}
QToolButton:disabled {{ color: #9A9A9A; }}

/* ------------------------------------------------------------ dock windows */
QDockWidget {{ color: {CAPTION_TEXT}; titlebar-close-icon: none; titlebar-normal-icon: none; }}
QDockWidget::title {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                stop:0 {CAPTION_TOP}, stop:1 {CAPTION_BOTTOM});
    border: 1px solid #B5C9DE;
    border-bottom: none;
    padding: 4px 7px;
    color: {CAPTION_TEXT};
    font-weight: 600;
}}
QDockWidget::close-button, QDockWidget::float-button {{
    background: transparent;
    border: none;
    padding: 0;
}}

/* -------------------------------------------------------- document tabs */
QTabWidget::pane {{
    border: 1px solid {BORDER};
    background: {PANEL};
    top: -1px;
}}
QTabBar {{ qproperty-drawBase: 0; }}
QTabBar::tab {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                stop:0 #E6E6E6, stop:1 #D2D2D2);
    border: 1px solid {BORDER};
    border-bottom: none;
    padding: 4px 14px;
    margin-right: 2px;
    min-width: 48px;
}}
QTabBar::tab:hover:!selected {{ background: #EDF4FC; }}
QTabBar::tab:selected {{
    background: {PANEL};
    border-top: 2px solid {ACCENT};
    padding-top: 3px;
}}
QTabBar::close-button {{
    subcontrol-position: right;
    margin-left: 4px;
}}

/* ------------------------------------------------------------ item views */
QTreeWidget, QListWidget, QTableWidget, QListView, QTreeView, QTableView {{
    background: #FFFFFF;
    alternate-background-color: #F5F8FB;
    border: 1px solid {BORDER};
    selection-background-color: {SELECT_BG};
    selection-color: {SELECT_FG};
    outline: none;
}}
QTreeView::item, QListView::item {{ padding: 2px 4px; }}
QTreeView::item:hover, QListView::item:hover {{ background: #EAF4FD; }}
QHeaderView::section {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                stop:0 #F4F4F4, stop:1 #E0E0E0);
    border: none;
    border-right: 1px solid #C5C5C5;
    border-bottom: 1px solid #C5C5C5;
    padding: 4px 6px;
    font-weight: 600;
}}
QTableWidget {{ gridline-color: #E0E0E0; }}

/* ---------------------------------------------------------------- inputs */
QLineEdit, QPlainTextEdit, QTextEdit, QTextBrowser {{
    background: #FFFFFF;
    border: 1px solid {BORDER};
    padding: 3px;
    selection-background-color: {SELECT_BG};
    selection-color: {SELECT_FG};
}}
QLineEdit:focus, QPlainTextEdit:focus, QTextEdit:focus {{ border: 1px solid {ACCENT}; }}
QPlainTextEdit#OutputView {{
    font-family: "Menlo", "Consolas", "DejaVu Sans Mono", monospace;
    font-size: 11px;
    background: #FFFFFF;
}}

QPushButton {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                stop:0 #F6F6F6, stop:1 #DCDCDC);
    border: 1px solid {BORDER};
    padding: 4px 14px;
    border-radius: 2px;
    min-width: 62px;
}}
QPushButton:hover {{ border: 1px solid {ACCENT}; background: #EAF4FD; }}
QPushButton:pressed {{ background: #D0E7FA; }}
QPushButton:disabled {{ color: #9A9A9A; background: #EFEFEF; }}
QPushButton:default {{ border: 1px solid {ACCENT}; }}

QCheckBox, QRadioButton {{ spacing: 6px; }}
QComboBox {{
    background: #FFFFFF;
    border: 1px solid {BORDER};
    padding: 3px 6px;
}}
QGroupBox {{
    border: 1px solid {BORDER_SOFT};
    margin-top: 8px;
    padding-top: 6px;
}}
QGroupBox::title {{ subcontrol-origin: margin; left: 8px; color: {CAPTION_TEXT}; }}

/* -------------------------------------------------------------- splitters */
QSplitter::handle {{ background: {BORDER_SOFT}; }}
QSplitter::handle:horizontal {{ width: 3px; }}
QSplitter::handle:vertical {{ height: 3px; }}

/* ------------------------------------------------------------- status bar */
QStatusBar {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                stop:0 {STATUS_TOP}, stop:1 {STATUS_BOTTOM});
    color: #FFFFFF;
    border-top: 1px solid #274F76;
}}
QStatusBar::item {{ border: none; }}
QStatusBar QLabel {{ color: #FFFFFF; padding: 1px 8px; }}
QStatusBar QLabel#StatusDim {{ color: #D6E4F2; }}

QScrollBar:vertical {{
    background: #F0F0F0; width: 13px; margin: 0;
    border-left: 1px solid #D6D6D6;
}}
QScrollBar::handle:vertical {{
    background: #CDCDCD; min-height: 24px; border: 1px solid #B5B5B5;
}}
QScrollBar::handle:vertical:hover {{ background: #BBD9F5; }}
QScrollBar::add-line, QScrollBar::sub-line {{ height: 0; width: 0; }}
QScrollBar:horizontal {{
    background: #F0F0F0; height: 13px; margin: 0;
    border-top: 1px solid #D6D6D6;
}}
QScrollBar::handle:horizontal {{
    background: #CDCDCD; min-width: 24px; border: 1px solid #B5B5B5;
}}

/* ----------------------------------------------------------- start page */
QLabel#WelcomeTitle {{
    font-size: 24px;
    font-weight: 600;
    color: {CAPTION_TEXT};
}}
QLabel#WelcomeSubtitle {{ color: {MUTED}; }}
QLabel#SectionHint {{ color: {MUTED}; }}
QLabel#SectionHeader {{
    font-weight: 600;
    color: {CAPTION_TEXT};
}}
QListWidget#RecentList {{ background: #FFFFFF; }}
QTableView {{ gridline-color: #E3E3E3; }}
"""


def apply_vs2010_theme(app: QApplication) -> None:
    app.setStyle("Fusion")
    palette = QPalette()
    palette.setColor(QPalette.ColorRole.Window, QColor(WINDOW))
    palette.setColor(QPalette.ColorRole.Base, QColor("#FFFFFF"))
    palette.setColor(QPalette.ColorRole.AlternateBase, QColor("#F5F8FB"))
    palette.setColor(QPalette.ColorRole.Text, QColor(TEXT))
    palette.setColor(QPalette.ColorRole.Highlight, QColor(SELECT_BG))
    palette.setColor(QPalette.ColorRole.HighlightedText, QColor(SELECT_FG))
    palette.setColor(QPalette.ColorRole.Button, QColor("#E4E4E4"))
    palette.setColor(QPalette.ColorRole.ButtonText, QColor(TEXT))
    app.setPalette(palette)
    app.setFont(QFont(app.font().family(), 9))
    app.setStyleSheet(VS2010_QSS)
