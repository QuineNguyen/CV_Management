-- =============================================================================
-- V7 - Full-text search over published CV content
-- =============================================================================

ALTER TABLE cv_versions
    ADD COLUMN content_text LONGTEXT NULL AFTER content_json;

ALTER TABLE cv_versions
    ADD FULLTEXT KEY ft_cv_versions_content (content_text);