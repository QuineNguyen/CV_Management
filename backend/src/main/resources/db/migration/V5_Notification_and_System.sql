-- =============================================================================
-- V5 - Notifications and system tables
-- email_logs, notifications, reminder_logs, system_configs, audit_logs
-- =============================================================================

-- Pure log: nothing in the domain joins to it. It deliberately stores no message body (real
-- storage cost, no reader) and no polymorphic target - the subject line is what anyone
-- investigating a delivery problem actually looks at.
CREATE TABLE email_logs (
                            id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            event_type      VARCHAR(40)  NOT NULL,
                            recipient_id    BIGINT UNSIGNED NOT NULL,
    -- Captured at send time. The account's address may change afterwards, and the log has to say
    -- where the message actually went.
                            recipient_email VARCHAR(255) NOT NULL,
                            subject         VARCHAR(255) NOT NULL,
                            status          VARCHAR(40)  NOT NULL DEFAULT 'PENDING',
                            retry_count     INT NOT NULL DEFAULT 0,
                            sent_at         DATETIME NULL,
                            error_message   TEXT NULL,
                            created_at      DATETIME NOT NULL,
                            PRIMARY KEY (id),
                            KEY ix_email_logs_recipient (recipient_id, created_at),
                            KEY ix_email_logs_status (status, retry_count),   -- drives the retry sweep
                            CONSTRAINT ck_email_logs_status CHECK (status IN ('PENDING','SENT','FAILED')),
                            CONSTRAINT fk_email_logs_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every event that sends an email also creates an in-app notification for the same person, so
-- someone who never opens their mail still sees what happened.
CREATE TABLE notifications (
                               id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                               recipient_id BIGINT UNSIGNED NOT NULL,
                               type         VARCHAR(40)  NOT NULL,
                               content      TEXT NOT NULL,
                               link         VARCHAR(512) NOT NULL,
                               is_read      BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at   DATETIME NOT NULL,
                               PRIMARY KEY (id),
                               KEY ix_notifications_recipient (recipient_id, is_read, created_at),
                               CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- This table exists to prevent duplicate reminders. The existence of a row is the entire
-- information it carries, which is why it has no status column: whether the message was
-- delivered belongs to email_logs.
CREATE TABLE reminder_logs (
                               id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                               target_type      VARCHAR(40) NOT NULL,
    -- Polymorphic, so no foreign key. Kept NOT NULL on purpose: NULLs do not collide in a unique
    -- index, so a nullable column here would silently disable the duplicate guard for any
    -- reminder that has no specific target. Those point at the recipient instead.
                               target_id        BIGINT UNSIGNED NOT NULL,
                               recipient_id     BIGINT UNSIGNED NOT NULL,
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
-- The four-column duplicate guard itself: see V6.

CREATE TABLE system_configs (
                                id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                config_key   VARCHAR(100) NOT NULL,
    -- Stored as text and coerced by the application, so adding a setting of a new type never
    -- requires a schema change.
                                config_value VARCHAR(255) NOT NULL,
                                description  TEXT NULL,
                                updated_by   BIGINT UNSIGNED NOT NULL,
                                updated_at   DATETIME NOT NULL,
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_system_configs_key (config_key),
                                CONSTRAINT fk_system_configs_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
                            id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- Always a real account. Background work records whoever set it in motion; there is no
    -- system user. The daily reminder job writes nothing here - it changes no business data.
                            actor_id    BIGINT UNSIGNED NOT NULL,
    -- Captured rather than looked up later: roles change and the user table keeps no history, so
    -- reading it back would report today's role for a decision made under a different one.
                            actor_role  VARCHAR(40) NOT NULL,
                            action      VARCHAR(64) NOT NULL,
                            target_type VARCHAR(40) NOT NULL,
    -- Polymorphic and intentionally without a foreign key: audit rows outlive what they describe,
    -- and a foreign key would either block deletion or take the history down with it.
                            target_id   BIGINT UNSIGNED NOT NULL,
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
-- No IP address column: no screen reads one, and part of the trail comes from scheduled and
-- async work that runs outside any HTTP request, where there would be no address to record.