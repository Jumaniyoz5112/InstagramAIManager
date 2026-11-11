--liquibase formatted sql

--changeset system:005-instagram-media-comments
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'instagram_media_comments'

CREATE TABLE instagram_media_comments (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    instagram_media_id      UUID          NOT NULL,
    ig_comment_id           VARCHAR(64)   NOT NULL,
    parent_ig_comment_id    VARCHAR(64),
    username                VARCHAR(255),
    text                    TEXT,
    like_count              INT,
    published_at            TIMESTAMPTZ,
    raw_payload             JSONB,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_instagram_media_comments_tenant_ig UNIQUE (tenant_id, ig_comment_id),
    CONSTRAINT fk_instagram_media_comments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_media_comments_media FOREIGN KEY (instagram_media_id) REFERENCES instagram_media (id) ON DELETE CASCADE
);

CREATE INDEX idx_instagram_media_comments_media ON instagram_media_comments (instagram_media_id);
CREATE INDEX idx_instagram_media_comments_parent ON instagram_media_comments (instagram_media_id, parent_ig_comment_id);

--rollback DROP TABLE IF EXISTS instagram_media_comments;
