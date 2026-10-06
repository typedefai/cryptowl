"""PySide6 UI for the devkit."""

from importlib.resources import files

from PySide6.QtGui import QIcon


def logo_path() -> str:
    """Path to the app logo (derived from the Android adaptive icon)."""
    return str(files("cryptowl_devkit").joinpath("assets", "logo.png"))


def app_icon() -> QIcon:
    return QIcon(logo_path())
