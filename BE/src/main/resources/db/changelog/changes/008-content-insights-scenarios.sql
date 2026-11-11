--liquibase formatted sql

--changeset system:008-content-insight-reports
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'content_insight_reports'

CREATE TABLE content_insight_reports (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    instagram_account_id    UUID,
    status                  VARCHAR(32)   NOT NULL,
    model_name              VARCHAR(128),
    prompt_version          VARCHAR(32),
    schema_version          VARCHAR(16),
    input_snapshot_json     JSONB,
    result_json             JSONB,
    error_message           VARCHAR(1000),
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_content_insight_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_content_insight_reports_account FOREIGN KEY (instagram_account_id) REFERENCES instagram_accounts (id) ON DELETE SET NULL
);

CREATE INDEX idx_content_insight_reports_tenant ON content_insight_reports (tenant_id, workspace_id, created_at DESC);
CREATE INDEX idx_content_insight_reports_status ON content_insight_reports (status, created_at ASC);

--changeset system:008-content-scenarios
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'content_scenarios'

CREATE TABLE content_scenarios (
    id                      UUID          PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    workspace_id            UUID          NOT NULL,
    insight_report_id       UUID          NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    model_name              VARCHAR(128),
    prompt_version          VARCHAR(32),
    schema_version          VARCHAR(16),
    brief                   VARCHAR(1000),
    result_json             JSONB,
    error_message           VARCHAR(1000),
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_content_scenarios_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_content_scenarios_report FOREIGN KEY (insight_report_id) REFERENCES content_insight_reports (id) ON DELETE CASCADE
);

CREATE INDEX idx_content_scenarios_tenant ON content_scenarios (tenant_id, workspace_id, created_at DESC);
CREATE INDEX idx_content_scenarios_report ON content_scenarios (insight_report_id, created_at DESC);

--rollback DROP TABLE IF EXISTS content_scenarios;
--rollback DROP TABLE IF EXISTS content_insight_reports;
