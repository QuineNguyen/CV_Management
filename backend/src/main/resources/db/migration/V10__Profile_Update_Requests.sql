-- =============================================================================
-- V10 - Profile update requests
-- Self-service profile updates. The user proposes a snapshot of the five
-- in-scope fields; the values land on `users` only when a reviewer approves.
-- =============================================================================

CREATE TABLE profile_update_requests (
                                         id                        UUID NOT NULL,
                                         user_id                   UUID NOT NULL,
                                         status                    VARCHAR(40) NOT NULL DEFAULT 'PENDING',

    -- Proposed values. Empty means "keep the current value", which is also why
    -- this path can never clear a field - an accepted consequence.
                                         requested_full_name       VARCHAR(255) NULL,
                                         requested_date_of_birth   DATE NULL,
                                         requested_phone_number    VARCHAR(30) NULL,
                                         requested_address         VARCHAR(500) NULL,
                                         requested_avatar_image_id UUID NULL,

    -- Review outcome. Empty while PENDING and after a withdrawal.
                                         reviewed_by               UUID NULL,
                                         reviewed_at               DATETIME NULL,
                                         reject_reason             TEXT NULL,

                                         created_by                UUID NOT NULL,
                                         created_at                DATETIME NOT NULL,
                                         updated_by                UUID NOT NULL,
                                         updated_at                DATETIME NOT NULL,

    -- Constraint: at most one PENDING request per user. The column is
    -- NULL for every terminal status, and NULL never collides in a MariaDB unique
    -- index, so APPROVED/REJECTED/CANCELLED history accumulates freely.
    -- This is the enforcement - a service-layer check is read-then-write and races.
                                         uk_pending_user UUID GENERATED ALWAYS AS
                                             (CASE WHEN status = 'PENDING' THEN user_id END) STORED,

                                         PRIMARY KEY (id),
                                         UNIQUE KEY uk_pur_pending_user (uk_pending_user),
                                         KEY ix_pur_user (user_id, created_at),
                                         KEY ix_pur_status (status, created_at),

                                         CONSTRAINT ck_pur_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    -- A rejection is an answer sent to someone; it must say why.
                                         CONSTRAINT ck_pur_reject_reason
                                             CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
    -- A reviewed request records who reviewed it and when. CANCELLED is the user's
    -- own withdrawal, so it carries no reviewer.
                                         CONSTRAINT ck_pur_reviewed
                                             CHECK (status NOT IN ('APPROVED','REJECTED')
                                                 OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),

                                         CONSTRAINT fk_pur_user       FOREIGN KEY (user_id)     REFERENCES users (id),
                                         CONSTRAINT fk_pur_reviewer   FOREIGN KEY (reviewed_by) REFERENCES users (id),
    -- SET NULL, not RESTRICT: this table is not immutable, so an image referenced
    -- only by a closed request must not become undeletable. cv_versions is where
    -- RESTRICT belongs.
                                         CONSTRAINT fk_pur_avatar     FOREIGN KEY (requested_avatar_image_id)
                                             REFERENCES image_files (id) ON DELETE SET NULL,
                                         CONSTRAINT fk_pur_created_by FOREIGN KEY (created_by) REFERENCES users (id),
                                         CONSTRAINT fk_pur_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;