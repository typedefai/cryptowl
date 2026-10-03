from __future__ import annotations

"""Value renderers / decoders for the grid, value panel and row viewers."""

import datetime as _dt
import json

from . import crockford32
from .cwo1 import HEADER_LEN, parse_header

CWO1_MAGIC = b"CWO1"


def fmt_ms(ms) -> str:
    """Epoch ms -> `YYYY-MM-DD HH:MM:SS` (raw ms kept as the tooltip)."""
    if ms in (None, ""):
        return ""
    try:
        return _dt.datetime.fromtimestamp(int(ms) / 1000).strftime("%Y-%m-%d %H:%M:%S")
    except (OverflowError, OSError, ValueError, TypeError):
        return str(ms)


def fmt_size(size) -> str:
    size = int(size or 0)
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size} {unit}" if unit == "B" else f"{size:.1f} {unit}"
        size /= 1024.0
    return f"{size} B"


def describe(value) -> str:
    """Compact cell preview: NULL dim, blobs as <blob N> / CWO1 summary."""
    if value is None:
        return "NULL"
    if isinstance(value, (bytes, bytearray, memoryview)):
        blob = bytes(value)
        if blob[:4] == CWO1_MAGIC:
            try:
                header = parse_header(blob)
                if header.is_chunked:
                    return f"<CWO1 chunked x{header.chunk_count}>"
                return f"<CWO1 whole {len(blob) - HEADER_LEN - 16 - 22}B>"
            except Exception:  # noqa: BLE001
                return f"<CWO1 broken {len(blob)}B>"
        return f"<blob {len(blob)}B>"
    if isinstance(value, int) and abs(value) > 10 ** 12:  # epoch-ms guess
        return fmt_ms(value)
    return str(value)


def hex_dump(data: bytes, limit: int = 8192) -> str:
    """Classic offset/hex/ascii dump, truncated to [limit] bytes."""
    data = bytes(data[:limit])
    lines = []
    for offset in range(0, len(data), 16):
        chunk = data[offset:offset + 16]
        hex_part = " ".join("%02x" % b for b in chunk)
        ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
        lines.append("%08x  %-48s  |%-16s|" % (offset, hex_part, ascii_part))
    if len(data) > limit:
        lines.append(f"… {len(data) - limit} more bytes (truncated at {limit})")
    return "\n".join(lines) if lines else "(empty)"


def ascii_part(chunk: bytes) -> str:
    return "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)


def pretty_json_if(text: str) -> str | None:
    """Pretty-prints when `text` parses as JSON; None otherwise."""
    if not text or text[0] not in "{[":
        return None
    try:
        return json.dumps(json.loads(text), indent=2, sort_keys=True)
    except ValueError:
        return None


def base32(data: bytes) -> str:
    return crockford32.encode(bytes(data))


def cwo1_summary(blob: bytes) -> dict:
    """(version, chunked, chunk_count, nonce/iv prefix) or None when not CWO1."""
    try:
        header = parse_header(blob)
    except Exception:  # noqa: BLE001
        return None
    return dict(version=header.version, chunked=header.is_chunked,
                chunk_size=header.chunk_size, chunk_count=header.chunk_count,
                iv_or_nonce=header.nonce_or_iv_prefix.hex())


CLASSIFICATION = {
    "C": "Confidential — SQLCipher only",
    "S": "Secret — per-item DEK (fingerprint on Android)",
    "T": "Top Secret — fingerprint + secondary password (Android)",
}
