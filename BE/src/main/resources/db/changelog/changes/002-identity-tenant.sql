--liquibase formatted sql

--changeset system:002-identity-tenant
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'

CREATE TABLE users (
    id              UUID          PRIMARY KEY,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    full_name       VARCHAR(255)  NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE roles (
    id              UUID          PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL UNIQUE,
    description     VARCHAR(255)
);

CREATE TABLE user_roles (
    user_id         UUID          NOT NULL,
    role_id         UUID          NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE tenants (
    id              UUID          PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    slug            VARCHAR(100)  NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE tenant_members (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    user_id         UUID          NOT NULL,
    role            VARCHAR(20)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_tenant_members_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_tenant_members_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tenant_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE workspaces (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_workspaces_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX idx_workspaces_tenant ON workspaces (tenant_id);

CREATE TABLE refresh_tokens (
    id                      UUID          PRIMARY KEY,
    user_id                 UUID          NOT NULL,
    token_hash              VARCHAR(128)  NOT NULL UNIQUE,
    expires_at              TIMESTAMPTZ   NOT NULL,
    revoked_at              TIMESTAMPTZ,
    replaced_by_token_id    UUID,
    created_at              TIMESTAMPTZ   NOT NULL,
    ip_address              VARCHAR(64),
    user_agent              VARCHAR(512),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

INSERT INTO roles (id, code, description)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'PLATFORM_ADMIN',
    'Global platform administrator'
);

--rollback DROP TABLE IF EXISTS refresh_tokens;
--rollback DROP TABLE IF EXISTS workspaces;
--rollback DROP TABLE IF EXISTS tenant_members;
--rollback DROP TABLE IF EXISTS tenants;
--rollback DROP TABLE IF EXISTS user_roles;
--rollback DROP TABLE IF EXISTS roles;
--rollback DROP TABLE IF EXISTS users;
