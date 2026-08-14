-- =============================================================================
-- V4 - Update requests
-- batch_requests, update_requests
-- =============================================================================

CREATE TABLE batch_requests (
                                id              UUID NOT NULL,
                                reason          TEXT NOT NULL,
                                deadline        DATETIME NOT NULL,
                                language        VARCHAR(40) NOT NULL,
                                target_type     VARCHAR(40) NOT NULL,
                                target_value    LONGTEXT NOT NULL,
                                total_count     INT NOT NULL DEFAULT 0,
                                processed_count INT NOT NULL DEFAULT 0,
                                error_count     INT NOT NULL DEFAULT 0,
                                status          VARCHAR(40) NOT NULL DEFAULT 'PROCESSING',
                                created_by      UUID NOT NULL,
                                created_at      DATETIME NOT NULL,
                                updated_by      UUID NOT NULL,
                                updated_at      DATETIME NOT NULL,
                                PRIMARY KEY (id),
                                KEY ix_batch_requests_status (status, created_at),
                                CONSTRAINT ck_batch_requests_language CHECK (language IN ('VI','EN','JA')),
                                CONSTRAINT ck_batch_requests_target   CHECK (target_type IN ('DEPARTMENT','TEAM','MANUAL')),
                                CONSTRAINT ck_batch_requests_status   CHECK (status IN
                                                                             ('PROCESSING','COMPLETED','COMPLETED_WITH_ERRORS')),
                                CONSTRAINT ck_batch_requests_value    CHECK (JSON_VALID(target_value)),
                                CONSTRAINT fk_batch_requests_created_by FOREIGN KEY (created_by) REFERENCES users (id),
                                CONSTRAINT fk_batch_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE update_requests (
                                 id                  UUID NOT NULL,
                                 employee_id         UUID NOT NULL,
                                 profile_id          UUID NULL,
                                 language            VARCHAR(40) NOT NULL,
                                 cv_id               UUID NULL,
                                 reason              TEXT NOT NULL,
                                 anchored_notes      LONGTEXT NULL,
                                 deadline            DATETIME NOT NULL,   -- the last second of the chosen day
                                 status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
                                 batch_request_id    UUID NULL,
                                 notification_failed BOOLEAN NOT NULL DEFAULT FALSE,
                                 cancelled_by        UUID NULL,
                                 cancelled_at        DATETIME NULL,
                                 completed_at        DATETIME NULL,
                                 created_by          UUID NOT NULL,
                                 created_at          DATETIME NOT NULL,
                                 updated_by          UUID NOT NULL,
                                 updated_at          DATETIME NOT NULL,
                                 PRIMARY KEY (id),
                                 KEY ix_update_requests_employee (employee_id, status),
                                 KEY ix_update_requests_deadline (status, deadline),
                                 KEY ix_update_requests_batch (batch_request_id, notification_failed),
                                 KEY ix_update_requests_cv_profile (cv_id, profile_id),
                                 CONSTRAINT ck_update_requests_language CHECK (language IN ('VI','EN','JA')),
                                 CONSTRAINT ck_update_requests_status   CHECK (status IN ('PENDING','COMPLETED','CANCELLED')),
                                 CONSTRAINT ck_update_requests_notes    CHECK (anchored_notes IS NULL OR JSON_VALID(anchored_notes)),
                                 CONSTRAINT fk_ur_employee     FOREIGN KEY (employee_id)      REFERENCES users (id),
                                 CONSTRAINT fk_ur_profile      FOREIGN KEY (profile_id)       REFERENCES cv_profiles (id),
                                 CONSTRAINT fk_ur_batch        FOREIGN KEY (batch_request_id) REFERENCES batch_requests (id),
                                 CONSTRAINT fk_ur_cancelled_by FOREIGN KEY (cancelled_by)     REFERENCES users (id),
                                 CONSTRAINT fk_ur_created_by   FOREIGN KEY (created_by)       REFERENCES users (id),
                                 CONSTRAINT fk_ur_updated_by   FOREIGN KEY (updated_by)       REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE update_requests
    ADD CONSTRAINT fk_ur_cv_profile FOREIGN KEY (cv_id, profile_id)
        REFERENCES cvs (id, profile_id);