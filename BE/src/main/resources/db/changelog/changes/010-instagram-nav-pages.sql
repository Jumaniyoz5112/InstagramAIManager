--liquibase formatted sql

--changeset system:010-instagram-nav-pages
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM app_pages WHERE code = 'IG_MEDIA'
INSERT INTO app_pages (code, name, description, path, sort_order, default_for_new_users, admin_only, enabled)
VALUES
    ('IG_MEDIA', 'Media', 'Instagram media, izoh va AI tahlil', '/app/instagram/media', 42, FALSE, FALSE, TRUE),
    ('IG_PUBLISH', 'Publish', 'Instagram nashr', '/app/instagram/publish', 44, FALSE, FALSE, TRUE),
    ('IG_INSIGHTS', 'Insights', 'Kontent chuqur tahlili va ssenariya', '/app/scenario', 46, FALSE, FALSE, TRUE),
    ('IG_SCENARIO', 'Scenario', 'Keyingi post ssenariyasi', '/app/instagram/scenario', 48, FALSE, FALSE, TRUE);

--changeset system:010-instagram-nav-page-grants
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM user_page_access WHERE page_code = 'IG_MEDIA'
INSERT INTO user_page_access (id, user_id, page_code, granted_by, created_at)
SELECT gen_random_uuid(), src.user_id, page.code, src.granted_by, NOW()
FROM user_page_access src
CROSS JOIN (VALUES ('IG_MEDIA'), ('IG_PUBLISH'), ('IG_INSIGHTS'), ('IG_SCENARIO')) AS page(code)
WHERE src.page_code = 'INSTAGRAM'
  AND NOT EXISTS (
      SELECT 1
      FROM user_page_access existing
      WHERE existing.user_id = src.user_id
        AND existing.page_code = page.code
  );

--rollback DELETE FROM user_page_access WHERE page_code IN ('IG_MEDIA', 'IG_PUBLISH', 'IG_INSIGHTS', 'IG_SCENARIO');
--rollback DELETE FROM app_pages WHERE code IN ('IG_MEDIA', 'IG_PUBLISH', 'IG_INSIGHTS', 'IG_SCENARIO');
