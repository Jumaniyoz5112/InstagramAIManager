--liquibase formatted sql

--changeset system:009-content-scenario-videos
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'content_scenario_videos'

CREATE TABLE content_scenario_videos (
    id                  UUID          PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    workspace_id        UUID          NOT NULL,
    scenario_id         UUID          NOT NULL,
    status              VARCHAR(32)   NOT NULL,
    storage_key         VARCHAR(512),
    content_type        VARCHAR(64),
    size_bytes          BIGINT,
    duration_sec        NUMERIC(6, 2),
    error_message       VARCHAR(1000),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_content_scenario_videos_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_content_scenario_videos_scenario FOREIGN KEY (scenario_id) REFERENCES content_scenarios (id) ON DELETE CASCADE
);

CREATE INDEX idx_content_scenario_videos_scenario ON content_scenario_videos (scenario_id, created_at DESC);

--rollback DROP TABLE IF EXISTS content_scenario_videos;
