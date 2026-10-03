from __future__ import annotations

"""Attachment helpers: enumerate/decrypt CWO1 files (C tier, FEK)."""

import os

from . import cwo1
from .cwo1 import Cwo1Error


def subdir_path(vault, subdir: str) -> str:
    if subdir not in ("attachments", "thumbnails"):
        raise ValueError(f"unknown subdir: {subdir}")
    return os.path.join(vault.path, subdir)


def list_files(vault, subdir: str) -> list:
    """Files on disk (not DB rows): name, size, cwo1 info, guessed AAD."""
    directory = subdir_path(vault, subdir)
    result = []
    if not os.path.isdir(directory):
        return result
    for name in sorted(os.listdir(directory)):
        full = os.path.join(directory, name)
        if not os.path.isfile(full):
            continue
        info = cwo1_info(full)
        result.append(dict(
            name=name,
            path=full,
            size=os.path.getsize(full),
            mtime=int(os.path.getmtime(full) * 1000),
            cwo1=info,
            aad=name[:-4] if name.endswith(".cwo") else name,
        ))
    return result


def cwo1_info(path: str) -> dict | None:
    try:
        with open(path, "rb") as f:
            head = f.read(cwo1.HEADER_LEN)
        header = cwo1.parse_header(head)
    except (OSError, Cwo1Error, ValueError):
        return None
    return dict(version=header.version, chunked=header.is_chunked,
                chunk_size=header.chunk_size, chunk_count=header.chunk_count,
                iv_or_nonce=header.nonce_or_iv_prefix.hex())


def decrypt_bytes(vault, aad: str, path: str) -> bytes | None:
    """Decrypts a whole-file CWO1 attachment with the vault FEK."""
    try:
        with open(path, "rb") as f:
            blob = f.read()
        return cwo1.decrypt_whole_file(vault.file_encryption_key,
                                       aad.encode("utf-8"), blob)
    except (OSError, Cwo1Error, ValueError):
        return None


def decrypt_to(vault, aad: str, path: str, target: str) -> int:
    """Streams a chunked (or whole) attachment to `target`; returns plain size."""
    blob = cwo1_info(path)
    if blob is None:
        raise ValueError(f"not a CWO1 file: {path}")
    if blob["chunked"]:
        return cwo1.decrypt_chunked_to_file(vault.file_encryption_key,
                                            aad.encode("utf-8"), path, target)
    plain = decrypt_bytes(vault, aad, path)
    if plain is None:
        raise ValueError(f"decryption failed for {path}")
    with open(target, "wb") as f:
        f.write(plain)
    return len(plain)
