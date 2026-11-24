--liquibase formatted sql

--changeset system:011-merge-instagram-nav-pages
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:2 SELECT COUNT(*) FROM app_pages WHERE code IN ('IG_PUBLISH', 'IG_SCENARIO') AND enabled = TRUE
UPDATE app_pages
SET enabled = FALSE
WHERE code IN ('IG_PUBLISH', 'IG_SCENARIO');

--rollback UPDATE app_pages SET enabled = TRUE WHERE code IN ('IG_PUBLISH', 'IG_SCENARIO');
