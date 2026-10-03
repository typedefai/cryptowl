#!/usr/bin/env python3
"""Entry point: `python run.py` (or `python -m cryptowl_desktop`)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from cryptowl_desktop.ui.main_window import main  # noqa: E402

if __name__ == "__main__":
    main()
