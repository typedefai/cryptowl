PRAGMA foreign_keys = ON;
PRAGMA secure_delete = ON;
CREATE TABLE t_moment (
    id                   CHAR(36) NOT NULL PRIMARY KEY,
    classification       CHAR(1)  NOT NULL DEFAULT 'C'
                         CHECK (classification IN ('C', 'S', 'T')),
    type                 TEXT     NOT NULL
                         CHECK (type IN ('text', 'media', 'link', 'location',
                                         'music', 'note', 'mini_program', 'live')),
    author_name          TEXT,
    author_username      TEXT,
    author_avatar_filename TEXT,
    content              TEXT,
    encrypted_data_id    CHAR(36),
    location             TEXT,
    visibility           TEXT     NOT NULL DEFAULT 'private'
                         CHECK (visibility IN ('private', 'friends')),
    source_id            TEXT     UNIQUE,
    source_created_at    INTEGER,
    like_count           INTEGER  NOT NULL DEFAULT 0,
    comment_count        INTEGER  NOT NULL DEFAULT 0,
    created_at           INTEGER  NOT NULL,
    updated_at           INTEGER  NOT NULL,
    deleted_at           INTEGER,
    FOREIGN KEY (encrypted_data_id) REFERENCES t_encrypted_data (id)
);
CREATE INDEX index_t_moment_source_created ON t_moment (source_created_at DESC);
CREATE INDEX index_t_moment_visibility ON t_moment (visibility);
CREATE TABLE t_moment_card (
    id                     CHAR(36) NOT NULL PRIMARY KEY,
    moment_id              CHAR(36) NOT NULL,
    card_type              TEXT     NOT NULL
                           CHECK (card_type IN ('link', 'video_link', 'finder',
                                                'music', 'mini_program', 'live',
                                                'note')),
    title                  TEXT,
    description            TEXT,
    source_name            TEXT,
    url                    TEXT,
    thumb_filename         TEXT,
    author_name            TEXT,
    author_avatar_filename TEXT,
    extra                  TEXT,
    created_at             INTEGER  NOT NULL,
    updated_at             INTEGER  NOT NULL,
    deleted_at             INTEGER,
    FOREIGN KEY (moment_id) REFERENCES t_moment (id) ON DELETE CASCADE
);
CREATE INDEX index_t_moment_card_moment ON t_moment_card (moment_id);
CREATE TABLE t_moment_media (
    id                 CHAR(36) NOT NULL PRIMARY KEY,
    moment_id          CHAR(36) NOT NULL,
    media_type         TEXT     NOT NULL CHECK (media_type IN ('image', 'video', 'audio')),
    classification     CHAR(1)  NOT NULL DEFAULT 'C'
                       CHECK (classification IN ('C', 'S', 'T')),
    filename           TEXT     NOT NULL,
    original_name      TEXT,
    mime_type          TEXT,
    size_bytes         INTEGER,
    width              INTEGER,
    height             INTEGER,
    duration_ms        INTEGER,
    thumbnail_filename TEXT,
    sort_order         INTEGER  NOT NULL DEFAULT 0,
    created_at         INTEGER  NOT NULL,
    updated_at         INTEGER  NOT NULL,
    deleted_at         INTEGER,
    FOREIGN KEY (moment_id) REFERENCES t_moment (id) ON DELETE CASCADE
);
CREATE INDEX index_t_moment_media_moment ON t_moment_media (moment_id, sort_order);
CREATE TABLE t_moment_comment (
    id                  CHAR(36) NOT NULL PRIMARY KEY,
    moment_id           CHAR(36) NOT NULL,
    parent_id           CHAR(36),
    author_name         TEXT,
    author_username     TEXT,
    content             TEXT,
    encrypted_data_id   CHAR(36),
    classification      CHAR(1)  NOT NULL DEFAULT 'C'
                        CHECK (classification IN ('C', 'S', 'T')),
    created_at          INTEGER  NOT NULL,
    deleted_at          INTEGER,
    FOREIGN KEY (moment_id) REFERENCES t_moment (id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES t_moment_comment (id),
    FOREIGN KEY (encrypted_data_id) REFERENCES t_encrypted_data (id)
);
CREATE INDEX index_t_moment_comment_moment ON t_moment_comment (moment_id, created_at);
CREATE TABLE t_moment_like (
    moment_id        CHAR(36) NOT NULL,
    author_username  TEXT     NOT NULL,
    author_name      TEXT,
    created_at       INTEGER  NOT NULL,
    PRIMARY KEY (moment_id, author_username),
    FOREIGN KEY (moment_id) REFERENCES t_moment (id) ON DELETE CASCADE
);
CREATE INDEX index_t_moment_like_author ON t_moment_like (author_username);
CREATE TABLE t_friend (
    id          CHAR(36) NOT NULL PRIMARY KEY,
    kind        TEXT     NOT NULL CHECK (kind IN ('ai', 'human')),
    name        TEXT     NOT NULL,
    model_id    TEXT,
    role_prompt TEXT,
    is_active   INTEGER  NOT NULL DEFAULT 1,
    created_at  INTEGER  NOT NULL,
    updated_at  INTEGER  NOT NULL,
    deleted_at  INTEGER
);
CREATE TABLE t_moment_share (
    friend_id  CHAR(36) NOT NULL,
    moment_id  CHAR(36) NOT NULL,
    scope      TEXT     NOT NULL DEFAULT 'full'
               CHECK (scope IN ('full', 'redacted')),
    granted_at INTEGER  NOT NULL,
    revoked_at INTEGER,
    PRIMARY KEY (friend_id, moment_id),
    FOREIGN KEY (friend_id) REFERENCES t_friend (id) ON DELETE CASCADE,
    FOREIGN KEY (moment_id) REFERENCES t_moment (id) ON DELETE CASCADE
);
CREATE INDEX index_t_moment_share_moment ON t_moment_share (moment_id);
CREATE TRIGGER trigger_moment_like_ai AFTER INSERT ON t_moment_like
BEGIN
    UPDATE t_moment SET like_count = like_count + 1
    WHERE id = NEW.moment_id;
END;
CREATE TRIGGER trigger_moment_like_ad AFTER DELETE ON t_moment_like
BEGIN
    UPDATE t_moment SET like_count = MAX(0, like_count - 1)
    WHERE id = OLD.moment_id;
END;
CREATE TRIGGER trigger_moment_comment_ai AFTER INSERT ON t_moment_comment
BEGIN
    UPDATE t_moment SET comment_count = comment_count + 1
    WHERE id = NEW.moment_id;
END;
CREATE TRIGGER trigger_moment_comment_ad AFTER UPDATE OF deleted_at ON t_moment_comment
WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
BEGIN
    UPDATE t_moment SET comment_count = MAX(0, comment_count - 1)
    WHERE id = OLD.moment_id;
END;
