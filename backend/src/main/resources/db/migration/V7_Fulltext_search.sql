-- =============================================================================
-- V7 - Full-text search over published CV content
--
-- content_text is the CV's text flattened out of its JSON document at publish time, written in
-- the same transaction. Because published versions are immutable, this column can never drift
-- from the document it was derived from: it is a projection frozen at the same instant, not a
-- second source of truth that has to be kept in sync.
-- =============================================================================

ALTER TABLE cv_versions
    ADD COLUMN content_text LONGTEXT NULL AFTER content_json;

ALTER TABLE cv_versions
    ADD FULLTEXT KEY ft_cv_versions_content (content_text);