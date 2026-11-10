--liquibase formatted sql

--changeset system:006-instagram-publish-jobs
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'instagram_publish_jobs'

CREATE TABLE instagram_publish_jobs (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    instagram_account_id    UUID          NOT NULL,
    publish_type            VARCHAR(32)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    caption                 TEXT,
    idempotency_key         VARCHAR(128),
    container_id            VARCHAR(64),
    published_ig_media_id   VARCHAR(64),
    error_message           VARCHAR(1000),
    created_by_user_id      UUID          NOT NULL,
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_instagram_publish_jobs_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_instagram_publish_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_publish_jobs_account FOREIGN KEY (instagram_account_id) REFERENCES instagram_accounts (id)
);

CREATE INDEX idx_instagram_publish_jobs_account ON instagram_publish_jobs (instagram_account_id, created_at DESC);
CREATE INDEX idx_instagram_publish_jobs_workspace ON instagram_publish_jobs (tenant_id, workspace_id, created_at DESC);

--changeset system:006-instagram-publish-assets
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'instagram_publish_assets'

CREATE TABLE instagram_publish_assets (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    publish_job_id          UUID          NOT NULL,
    position                INT           NOT NULL,
    content_type            VARCHAR(128),
    original_filename       VARCHAR(255),
    storage_key             VARCHAR(512)  NOT NULL,
    public_url              TEXT,
    child_container_id      VARCHAR(64),
    size_bytes              BIGINT,
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_instagram_publish_assets_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_publish_assets_job FOREIGN KEY (publish_job_id) REFERENCES instagram_publish_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_instagram_publish_assets_job ON instagram_publish_assets (publish_job_id, position);

--rollback DROP TABLE IF EXISTS instagram_publish_assets;
--rollback DROP TABLE IF EXISTS instagram_publish_jobs;
