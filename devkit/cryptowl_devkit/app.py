from __future__ import annotations

"""Entry point: `python run.py [vault-folder]` or `cryptowl-devkit`."""

import argparse
import logging
import sys

from PySide6.QtWidgets import QApplication

from .ui.main_window import MainWindow


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="cryptowl-devkit",
        description="Minimal CryptOwl vault developer toolkit")
    parser.add_argument("vault", nargs="?", help="vault folder to prefill")
    parser.add_argument("--debug", action="store_true", help="verbose logging")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.debug else logging.INFO,
        format="%(asctime)s %(levelname)-5s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    app = QApplication(sys.argv[:1])
    app.setApplicationName("CryptOwl DevKit")
    app.setOrganizationName("CryptOwl")
    window = MainWindow(start_dir=args.vault)
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
