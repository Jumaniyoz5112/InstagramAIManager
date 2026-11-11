--liquibase formatted sql

--changeset system:007-content-analyses
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'content_analyses'

CREATE TABLE content_analyses (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    instagram_media_id      UUID          NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    analysis_kind           VARCHAR(32)   NOT NULL,
    model_name              VARCHAR(128),
    prompt_version          VARCHAR(32),
    error_message           VARCHAR(1000),
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_content_analyses_media_kind UNIQUE (instagram_media_id, analysis_kind),
    CONSTRAINT fk_content_analyses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_content_analyses_media FOREIGN KEY (instagram_media_id) REFERENCES instagram_media (id) ON DELETE CASCADE
);

CREATE INDEX idx_content_analyses_status ON content_analyses (status, created_at ASC);
CREATE INDEX idx_content_analyses_tenant ON content_analyses (tenant_id, workspace_id);

--changeset system:007-content-analysis-results
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'content_analysis_results'

CREATE TABLE content_analysis_results (
    id                      UUID          PRIMARY KEY,
    content_analysis_id     UUID          NOT NULL,
    tenant_id               UUID          NOT NULL,
    schema_version          VARCHAR(16)   NOT NULL,
    result_json             JSONB         NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_content_analysis_results_analysis UNIQUE (content_analysis_id),
    CONSTRAINT fk_content_analysis_results_analysis FOREIGN KEY (content_analysis_id) REFERENCES content_analyses (id) ON DELETE CASCADE,
    CONSTRAINT fk_content_analysis_results_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

--changeset system:007-media-engagement-snapshots
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'media_engagement_snapshots'

CREATE TABLE media_engagement_snapshots (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    instagram_media_id      UUID          NOT NULL,
    like_count              INT,
    comments_count          INT,
    shares_count            INT,
    saves_count             INT,
    comments_sample         JSONB,
    slot                    VARCHAR(16)   NOT NULL,
    captured_at             TIMESTAMPTZ   NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_media_engagement_snapshots_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_media_engagement_snapshots_media FOREIGN KEY (instagram_media_id) REFERENCES instagram_media (id) ON DELETE CASCADE
);

CREATE INDEX idx_media_engagement_snapshots_media ON media_engagement_snapshots (instagram_media_id, captured_at DESC);
CREATE INDEX idx_media_engagement_snapshots_slot ON media_engagement_snapshots (tenant_id, captured_at DESC);

--rollback DROP TABLE IF EXISTS media_engagement_snapshots;
--rollback DROP TABLE IF EXISTS content_analysis_results;
--rollback DROP TABLE IF EXISTS content_analyses;
