PRAGMA foreign_keys = ON;
PRAGMA secure_delete = ON;
CREATE TABLE t_note (
    id                CHAR(36) NOT NULL PRIMARY KEY,
    classification    CHAR(1)  NOT NULL DEFAULT 'C'
                      CHECK (classification IN ('C', 'S', 'T')),
    title             TEXT     NOT NULL DEFAULT '',
    content           TEXT,
    pinned            INTEGER  NOT NULL DEFAULT 0,
    encrypted_data_id CHAR(36),
    created_at        INTEGER  NOT NULL,
    updated_at        INTEGER  NOT NULL,
    deleted_at        INTEGER,
    FOREIGN KEY (encrypted_data_id) REFERENCES t_encrypted_data (id)
);
CREATE INDEX index_t_note_deleted_at ON t_note (deleted_at, updated_at DESC);
CREATE INDEX index_t_note_pinned ON t_note (pinned, updated_at DESC);
