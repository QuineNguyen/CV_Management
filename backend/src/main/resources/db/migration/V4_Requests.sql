-- =============================================================================
-- V4 - Update requests
-- batch_requests, update_requests
-- =============================================================================

CREATE TABLE batch_requests (
                                id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                reason          TEXT NOT NULL,
                                deadline        DATETIME NOT NULL,   -- the last second of the chosen day
                                language        VARCHAR(40) NOT NULL,
                                target_type     VARCHAR(40) NOT NULL,
                                target_value    LONGTEXT NOT NULL,
                                total_count     INT NOT NULL DEFAULT 0,
                                processed_count INT NOT NULL DEFAULT 0,
    -- Non-zero exactly when the status records partial failure. Keeping both lets the summary
    -- screen show how many failed without re-scanning the individual requests.
                                error_count     INT NOT NULL DEFAULT 0,
                                status          VARCHAR(40) NOT NULL DEFAULT 'PROCESSING',
                                created_by      BIGINT UNSIGNED NOT NULL,
                                created_at      DATETIME NOT NULL,
                                updated_by      BIGINT UNSIGNED NOT NULL,
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
                                 id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                 employee_id         BIGINT UNSIGNED NOT NULL,
    -- May be empty, which means "create a profile and a CV". Requiring a profile would make it
    -- impossible to ask an employee who has none yet to get started.
                                 profile_id          BIGINT UNSIGNED NULL,
                                 language            VARCHAR(40) NOT NULL,
    -- Filled in later, when the CV the request asked for actually exists.
                                 cv_id               BIGINT UNSIGNED NULL,
                                 reason              TEXT NOT NULL,
    -- Notes anchored to positions in the CV, using the shared anchor convention.
                                 anchored_notes      LONGTEXT NULL,
                                 deadline            DATETIME NOT NULL,   -- the last second of the chosen day
                                 status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
                                 batch_request_id    BIGINT UNSIGNED NULL,
    -- Set when the notification email could not be delivered, so the batch summary can list the
    -- people who were never actually told.
                                 notification_failed BOOLEAN NOT NULL DEFAULT FALSE,
                                 cancelled_by        BIGINT UNSIGNED NULL,
                                 cancelled_at        DATETIME NULL,
    -- Set when a new version is published, not when the CV is first created: creating an empty
    -- CV does not satisfy a request to update one.
                                 completed_at        DATETIME NULL,
                                 created_by          BIGINT UNSIGNED NOT NULL,
                                 created_at          DATETIME NOT NULL,
                                 updated_by          BIGINT UNSIGNED NOT NULL,
                                 updated_at          DATETIME NOT NULL,
                                 PRIMARY KEY (id),
                                 KEY ix_update_requests_employee (employee_id, status),
                                 KEY ix_update_requests_deadline (status, deadline),        -- drives the reminder job
                                 KEY ix_update_requests_batch (batch_request_id, notification_failed),
                                 KEY ix_update_requests_cv_profile (cv_id, profile_id),     -- supports the composite key below
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

-- A composite foreign key, so the database itself refuses to link a CV that belongs to a
-- different profile than the one the request targets. This is the point of the otherwise
-- redundant unique key on cvs (id, profile_id) in V2.
--
-- MariaDB skips the check when any column in the pair is NULL, which is exactly the behaviour
-- needed here: a request with no CV yet, or no target profile, is not blocked. The consequence
-- for the service layer is that linking must set both columns in a single UPDATE - setting
-- only cv_id would leave profile_id NULL and skip the very check this exists for.
ALTER TABLE update_requests
    ADD CONSTRAINT fk_ur_cv_profile FOREIGN KEY (cv_id, profile_id)
        REFERENCES cvs (id, profile_id);