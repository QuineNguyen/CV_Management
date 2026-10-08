-- =============================================================================
-- V13 - Batch request cancellation
-- A batch can be cancelled as a whole; its PENDING children are cancelled with it.
-- =============================================================================

ALTER TABLE batch_requests DROP CONSTRAINT ck_batch_requests_status;

ALTER TABLE batch_requests ADD CONSTRAINT ck_batch_requests_status
    CHECK (status IN ('PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'CANCELLED'));