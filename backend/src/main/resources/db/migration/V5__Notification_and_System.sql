-- =============================================================================
-- V5 - Notifications and system tables
-- email_logs, notifications, reminder_logs, system_configs, audit_logs
-- =============================================================================

CREATE TABLE email_logs (
                            id              UUID NOT NULL,
                            event_type      VARCHAR(40)  NOT NULL,
                            recipient_id    UUID NOT NULL,
                            recipient_email VARCHAR(255) NOT NULL,
                            subject         VARCHAR(255) NOT NULL,
                            status          VARCHAR(40)  NOT NULL DEFAULT 'PENDING',
                            retry_count     INT NOT NULL DEFAULT 0,
                            sent_at         DATETIME NULL,
                            error_message   TEXT NULL,
                            created_at      DATETIME NOT NULL,
                            PRIMARY KEY (id),
                            KEY ix_email_logs_recipient (recipient_id, created_at),
                            KEY ix_email_logs_status (status, retry_count),
                            CONSTRAINT ck_email_logs_status CHECK (status IN ('PENDING','SENT','FAILED')),
                            CONSTRAINT fk_email_logs_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
                               id           UUID NOT NULL,
                               recipient_id UUID NOT NULL,
                               type         VARCHAR(40)  NOT NULL,
                               content      TEXT NOT NULL,
                               link         VARCHAR(512) NOT NULL,
                               is_read      BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at   DATETIME NOT NULL,
                               PRIMARY KEY (id),
                               KEY ix_notifications_recipient (recipient_id, is_read, created_at),
                               CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reminder_logs (
                               id               UUID NOT NULL,
                               target_type      VARCHAR(40) NOT NULL,
                               target_id        UUID NOT NULL,
                               recipient_id     UUID NOT NULL,
                               escalation_level VARCHAR(40) NOT NULL,
                               sent_date        DATE NOT NULL,
                               PRIMARY KEY (id),
                               KEY ix_reminder_logs_recipient (recipient_id, sent_date),
                               CONSTRAINT ck_reminder_logs_target CHECK (target_type IN
                                                                         ('UPDATE_REQUEST','APPROVAL_ASSIGNMENT','SLA_DIGEST')),
                               CONSTRAINT ck_reminder_logs_level CHECK (escalation_level IN
                                                                        ('APPROACHING','LAST_DAY','OVERDUE','SLA_OVERDUE')),
                               CONSTRAINT fk_reminder_logs_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE system_configs (
                                id           UUID NOT NULL,
                                config_key   VARCHAR(100) NOT NULL,
                                config_value VARCHAR(255) NOT NULL,
                                description  TEXT NULL,
                                updated_by   UUID NOT NULL,
                                updated_at   DATETIME NOT NULL,
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_system_configs_key (config_key),
                                CONSTRAINT fk_system_configs_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
                            id          UUID NOT NULL,
                            actor_id    UUID NOT NULL,
                            actor_role  VARCHAR(40) NOT NULL,
                            action      VARCHAR(64) NOT NULL,
                            target_type VARCHAR(40) NOT NULL,
                            target_id   UUID NOT NULL,
                            old_value   TEXT NULL,
                            new_value   TEXT NULL,
                            created_at  DATETIME NOT NULL,
                            PRIMARY KEY (id),
                            KEY ix_audit_logs_actor (actor_id, created_at),
                            KEY ix_audit_logs_target (target_type, target_id, created_at),
                            KEY ix_audit_logs_time (created_at),
                            CONSTRAINT ck_audit_logs_role CHECK (actor_role IN ('ADMIN','HR','TECH_LEAD','EMPLOYEE')),
                            CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;