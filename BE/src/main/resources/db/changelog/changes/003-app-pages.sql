--liquibase formatted sql

--changeset system:003-app-pages
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'app_pages'

CREATE TABLE app_pages (
    code                VARCHAR(50)   PRIMARY KEY,
    name                VARCHAR(100)  NOT NULL,
    description         VARCHAR(255),
    path                VARCHAR(120)  NOT NULL,
    sort_order          INT           NOT NULL,
    default_for_new_users BOOLEAN     NOT NULL DEFAULT FALSE,
    admin_only          BOOLEAN       NOT NULL DEFAULT FALSE,
    enabled             BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE user_page_access (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,
    page_code       VARCHAR(50)   NOT NULL,
    granted_by      UUID,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_user_page_access UNIQUE (user_id, page_code),
    CONSTRAINT fk_user_page_access_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_page_access_page FOREIGN KEY (page_code) REFERENCES app_pages (code),
    CONSTRAINT fk_user_page_access_granted_by FOREIGN KEY (granted_by) REFERENCES users (id)
);

CREATE INDEX idx_user_page_access_user ON user_page_access (user_id);

INSERT INTO app_pages (code, name, description, path, sort_order, default_for_new_users, admin_only, enabled)
VALUES
    ('DASHBOARD', 'Dashboard', 'Asosiy ko‘rinish', '/app/dashboard', 10, TRUE, FALSE, TRUE),
    ('TENANTS', 'Tenants', 'Tenantlar boshqaruvi', '/app/tenants', 20, FALSE, FALSE, TRUE),
    ('WORKSPACES', 'Workspaces', 'Workspace kontekstlari', '/app/workspaces', 30, FALSE, FALSE, TRUE),
    ('ACCESS', 'Access', 'Foydalanuvchi sahifa ruxsatlari', '/app/access', 90, FALSE, TRUE, TRUE);

--rollback DROP TABLE IF EXISTS user_page_access;
--rollback DROP TABLE IF EXISTS app_pages;
