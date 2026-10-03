from __future__ import annotations

"""CWO1 — self-describing encrypted media format (docs/moments.md §4),
byte-exact with the Android `Cwo1.kt` and `wechat_sns_export/migrate_moments.py`.

    whole-file (images/thumbnails/covers):
      header 22 B = b"CWO1" | u16 version(1) | u32 chunk_size(0) | nonce(12)
      payload     = AES-256-GCM(FEK, data, AAD = row id) || tag(16)

    chunked (video/audio):
      header 22 B = b"CWO1" | u16 version(1) | u32 chunk_size(65536)
                    | u64 chunk_count | iv_prefix(4)
      record N    = AES-256-GCM(FEK, chunk N, nonce = u64(N) || iv_prefix) || tag(16)

`chunk_size == 0` marks whole-file mode. Records are random-access:
record N starts at `22 + N * (chunk_size + 16)`.
"""

import os
import secrets
import struct

from .crypto import NONCE_LEN, TAG_LEN, aes_gcm_decrypt, aes_gcm_encrypt

MAGIC = b"CWO1"
VERSION = 1
HEADER_LEN = 22
CHUNK_SIZE = 65536


class Cwo1Error(ValueError):
    pass


class Header:
    __slots__ = ("version", "chunk_size", "chunk_count", "nonce_or_iv_prefix")

    def __init__(self, version: int, chunk_size: int, chunk_count: int,
                 nonce_or_iv_prefix: bytes):
        self.version = version
        self.chunk_size = chunk_size
        self.chunk_count = chunk_count
        self.nonce_or_iv_prefix = nonce_or_iv_prefix

    @property
    def is_chunked(self) -> bool:
        return self.chunk_size > 0

    @property
    def record_length(self) -> int:
        return self.chunk_size + TAG_LEN


def parse_header(data: bytes) -> Header:
    if len(data) < HEADER_LEN:
        raise Cwo1Error("too short for CWO1 header")
    if data[:4] != MAGIC:
        raise Cwo1Error("not a CWO1 file")
    version, chunk_size = struct.unpack(">HI", data[4:10])
    if version != VERSION:
        raise Cwo1Error(f"unsupported CWO1 version: {version}")
    if chunk_size == 0:
        return Header(version, 0, 0, data[10:22])
    (count,) = struct.unpack(">Q", data[10:18])
    return Header(version, chunk_size, count, data[18:22])


def header_whole(nonce: bytes) -> bytes:
    if len(nonce) != NONCE_LEN:
        raise ValueError("nonce must be 12 bytes")
    return MAGIC + struct.pack(">HI", VERSION, 0) + nonce


def header_chunked(chunk_count: int, iv_prefix: bytes) -> bytes:
    if len(iv_prefix) != 4:
        raise ValueError("iv_prefix must be 4 bytes")
    return MAGIC + struct.pack(">HIQ", VERSION, CHUNK_SIZE, chunk_count) + iv_prefix


def chunk_nonce(index: int, iv_prefix: bytes) -> bytes:
    return struct.pack(">Q", index) + iv_prefix


# ----------------------------------------------------------------- whole-file

def encrypt_whole_file(fek: bytes, aad: bytes, plaintext: bytes,
                       nonce: bytes | None = None) -> bytes:
    nonce = nonce or secrets.token_bytes(NONCE_LEN)
    ciphertext, tag = aes_gcm_encrypt(fek, nonce, aad, plaintext)
    return header_whole(nonce) + ciphertext + tag


def decrypt_whole_file(fek: bytes, aad: bytes, data: bytes) -> bytes:
    header = parse_header(data)
    if header.is_chunked:
        raise Cwo1Error("not a whole-file CWO1 blob")
    return aes_gcm_decrypt(fek, header.nonce_or_iv_prefix, aad,
                           data[HEADER_LEN:-TAG_LEN], data[-TAG_LEN:])


# ------------------------------------------------------------------- chunked

def encrypt_chunked(fek: bytes, aad: bytes, plaintext: bytes,
                    iv_prefix: bytes | None = None) -> bytes:
    iv_prefix = iv_prefix or secrets.token_bytes(4)
    count = (len(plaintext) + CHUNK_SIZE - 1) // CHUNK_SIZE
    out = bytearray(header_chunked(count, iv_prefix))
    for index in range(count):
        chunk = plaintext[index * CHUNK_SIZE:(index + 1) * CHUNK_SIZE]
        ciphertext, tag = aes_gcm_encrypt(
            fek, chunk_nonce(index, iv_prefix), aad, chunk)
        out += ciphertext + tag
    return bytes(out)


def decrypt_chunk_at(fek: bytes, aad: bytes, data: bytes, index: int) -> bytes:
    header = parse_header(data)
    if not header.is_chunked:
        raise Cwo1Error("not a chunked CWO1 blob")
    if not 0 <= index < header.chunk_count:
        raise Cwo1Error("chunk index out of range")
    # the last record is shorter: total ciphertext = file - header - all tags
    total_ct = len(data) - HEADER_LEN - header.chunk_count * TAG_LEN
    chunk_len = min(header.chunk_size, total_ct - index * header.chunk_size)
    offset = HEADER_LEN + index * header.record_length
    return aes_gcm_decrypt(fek, chunk_nonce(index, header.nonce_or_iv_prefix), aad,
                           data[offset:offset + chunk_len],
                           data[offset + chunk_len:offset + chunk_len + TAG_LEN])


def decrypt_chunked(fek: bytes, aad: bytes, data: bytes) -> bytes:
    header = parse_header(data)
    if not header.is_chunked:
        raise Cwo1Error("not a chunked CWO1 blob")
    return b"".join(decrypt_chunk_at(fek, aad, data, i)
                    for i in range(header.chunk_count))


def encrypt_file_chunked(fek: bytes, aad: bytes, source_path: str,
                         target_path: str) -> int:
    """Streams [source_path] into chunked CWO1 records; returns the chunk count.

    The plaintext is read directly from disk into per-chunk buffers and never
    held in memory whole (large videos).
    """
    total = os.path.getsize(source_path)
    count = (total + CHUNK_SIZE - 1) // CHUNK_SIZE if total else 0
    iv_prefix = secrets.token_bytes(4)
    index = 0
    with open(source_path, "rb") as src, open(target_path, "wb") as dst:
        dst.write(header_chunked(count, iv_prefix))
        while True:
            chunk = src.read(CHUNK_SIZE)
            if not chunk:
                break
            ciphertext, tag = aes_gcm_encrypt(
                fek, chunk_nonce(index, iv_prefix), aad, chunk)
            dst.write(ciphertext)
            dst.write(tag)
            index += 1
    if index != count:
        raise Cwo1Error(f"chunk count mismatch: read {index}, expected {count}")
    return count


def decrypt_chunked_to_file(fek: bytes, aad: bytes, source_path: str,
                            target_path: str) -> int:
    """Streams a chunked CWO1 file back to plaintext; returns the plain size."""
    with open(source_path, "rb") as src:
        source_size = os.fstat(src.fileno()).st_size
        header = parse_header(src.read(HEADER_LEN))
        if not header.is_chunked:
            raise Cwo1Error("not a chunked CWO1 file")
        total_ct = source_size - HEADER_LEN - header.chunk_count * TAG_LEN
        written = 0
        with open(target_path, "wb") as dst:
            for index in range(header.chunk_count):
                chunk_len = min(header.chunk_size,
                                total_ct - index * header.chunk_size)
                ciphertext = src.read(chunk_len)
                tag = src.read(TAG_LEN)
                plain = aes_gcm_decrypt(
                    fek, chunk_nonce(index, header.nonce_or_iv_prefix), aad,
                    ciphertext, tag)
                dst.write(plain)
                written += len(plain)
        return written


def is_cwo1(path: str) -> bool:
    try:
        with open(path, "rb") as f:
            return f.read(4) == MAGIC
    except OSError:
        return False
