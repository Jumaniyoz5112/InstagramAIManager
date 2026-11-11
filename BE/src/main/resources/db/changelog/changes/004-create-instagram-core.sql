--liquibase formatted sql

--changeset system:004-instagram-page
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM app_pages WHERE code = 'INSTAGRAM'
INSERT INTO app_pages (code, name, description, path, sort_order, default_for_new_users, admin_only, enabled)
VALUES ('INSTAGRAM', 'Instagram', 'Instagram ulanish va postlar', '/app/instagram', 40, FALSE, FALSE, TRUE);

--changeset system:004-instagram-core
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'instagram_accounts'

CREATE TABLE instagram_accounts (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    ig_user_id              VARCHAR(64)   NOT NULL,
    username                VARCHAR(255),
    account_type            VARCHAR(40),
    access_token_encrypted  TEXT          NOT NULL,
    token_expires_at        TIMESTAMPTZ,
    scopes                  VARCHAR(512),
    status                  VARCHAR(32)   NOT NULL,
    last_synced_at          TIMESTAMPTZ,
    created_by_user_id      UUID          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_instagram_accounts_tenant_ig UNIQUE (tenant_id, ig_user_id),
    CONSTRAINT fk_instagram_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_accounts_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_instagram_accounts_user FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_instagram_accounts_workspace ON instagram_accounts (workspace_id);
CREATE INDEX idx_instagram_accounts_status ON instagram_accounts (status);

CREATE TABLE instagram_oauth_states (
    id                  UUID          PRIMARY KEY,
    state               VARCHAR(128)  NOT NULL UNIQUE,
    user_id             UUID          NOT NULL,
    tenant_id           UUID          NOT NULL,
    workspace_id        UUID          NOT NULL,
    expires_at          TIMESTAMPTZ   NOT NULL,
    consumed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_instagram_oauth_states_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_instagram_oauth_states_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_oauth_states_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
);

CREATE INDEX idx_instagram_oauth_states_expires ON instagram_oauth_states (expires_at);

CREATE TABLE instagram_media (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    instagram_account_id    UUID          NOT NULL,
    ig_media_id             VARCHAR(64)   NOT NULL,
    media_type              VARCHAR(32)   NOT NULL,
    caption                 TEXT,
    media_url               TEXT,
    thumbnail_url           TEXT,
    permalink               TEXT,
    published_at            TIMESTAMPTZ,
    media_url_fetched_at    TIMESTAMPTZ,
    like_count              INT,
    comments_count          INT,
    shares_count            INT,
    saves_count             INT,
    insights_synced_at      TIMESTAMPTZ,
    analysis_status         VARCHAR(32)   NOT NULL,
    raw_payload             JSONB,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_instagram_media_tenant_ig UNIQUE (tenant_id, ig_media_id),
    CONSTRAINT fk_instagram_media_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_media_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_instagram_media_account FOREIGN KEY (instagram_account_id) REFERENCES instagram_accounts (id)
);

CREATE INDEX idx_instagram_media_account ON instagram_media (instagram_account_id);
CREATE INDEX idx_instagram_media_workspace ON instagram_media (workspace_id);
CREATE INDEX idx_instagram_media_analysis ON instagram_media (analysis_status);
CREATE INDEX idx_instagram_media_published ON instagram_media (published_at DESC);

CREATE TABLE instagram_media_children (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    parent_media_id         UUID          NOT NULL,
    ig_media_id             VARCHAR(64)   NOT NULL,
    media_type              VARCHAR(32)   NOT NULL,
    media_url               TEXT,
    thumbnail_url           TEXT,
    position                INT           NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_instagram_media_children_parent_ig UNIQUE (parent_media_id, ig_media_id),
    CONSTRAINT fk_instagram_media_children_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_instagram_media_children_parent FOREIGN KEY (parent_media_id) REFERENCES instagram_media (id) ON DELETE CASCADE
);

CREATE INDEX idx_instagram_media_children_parent ON instagram_media_children (parent_media_id);

CREATE TABLE sync_jobs (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    instagram_account_id    UUID          NOT NULL,
    job_type                VARCHAR(40)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    cursor_after            VARCHAR(512),
    pages_fetched           INT           NOT NULL DEFAULT 0,
    items_upserted          INT           NOT NULL DEFAULT 0,
    error_message           VARCHAR(1000),
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_sync_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_sync_jobs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_sync_jobs_account FOREIGN KEY (instagram_account_id) REFERENCES instagram_accounts (id)
);

CREATE INDEX idx_sync_jobs_account_status ON sync_jobs (instagram_account_id, status);
CREATE INDEX idx_sync_jobs_created ON sync_jobs (created_at DESC);

--rollback DROP TABLE IF EXISTS sync_jobs;
--rollback DROP TABLE IF EXISTS instagram_media_children;
--rollback DROP TABLE IF EXISTS instagram_media;
--rollback DROP TABLE IF EXISTS instagram_oauth_states;
--rollback DROP TABLE IF EXISTS instagram_accounts;
