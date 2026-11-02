--liquibase formatted sql

--changeset system:001-health-ping
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'app_meta'
CREATE TABLE app_meta (
    id          BIGSERIAL PRIMARY KEY,
    key         VARCHAR(100)  NOT NULL UNIQUE,
    value       VARCHAR(500)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

INSERT INTO app_meta (key, value, created_at)
VALUES ('app_name', 'InstagramAIManager', CURRENT_TIMESTAMP);

--rollback DROP TABLE IF EXISTS app_meta;
