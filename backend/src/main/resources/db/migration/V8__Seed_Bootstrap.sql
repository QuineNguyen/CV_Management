-- =============================================================================
-- V8 - Bootstrap Seed
--
-- Initializes baseline system entities for an empty database (Root Dept, Root Admin,
-- Root Tech Lead, Root Team, and System Configurations).
-- =============================================================================

SET @is_empty = (SELECT COUNT(*) FROM users);

SET FOREIGN_KEY_CHECKS = 0;

-- 1) Root department ----------------------------------------------------------
INSERT INTO departments (id, code, name, parent_department_id, display_order,
                         created_by, created_at, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c01', '${seedDepartmentCode}', '${seedDepartmentName}', NULL, 10,
       '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW(), '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

-- 2) Root administrator -------------------------------------------------------
INSERT INTO users (id, full_name, email, username, password_hash, role,
                   primary_department_id, status, must_change_password,
                   failed_login_count, token_valid_from,
                   created_by, created_at, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', 'Root administrator', '${seedAdminEmail}', '${seedAdminUsername}',
       '${seedPasswordHash}', 'ADMIN', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c01', 'ACTIVE',
       TRUE,                 -- must choose a new password at first sign-in
    -- UTC_TIMESTAMP(), not NOW(): the app reads this column as UTC
    -- (hibernate.jdbc.time_zone=UTC). NOW() follows the session time zone.
        0, UTC_TIMESTAMP(),
       '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW(), '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()    -- self-referencing: this is the first row in the system
WHERE @is_empty = 0;

-- 3) Root tech lead, needed so the root team has an active lead ----------------
INSERT INTO users (id, full_name, email, username, password_hash, role,
                   primary_department_id, status, must_change_password,
                   failed_login_count, token_valid_from,
                   created_by, created_at, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c03', 'Root tech lead', '${seedTechLeadEmail}', '${seedTechLeadUsername}',
       '${seedPasswordHash}', 'TECH_LEAD', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c01', 'ACTIVE',
       TRUE, 0, UTC_TIMESTAMP(),
       '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW(), '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

-- 4) Root team ----------------------------------------------------------------
INSERT INTO teams (id, code, name, description, department_id, tech_lead_id,
                   display_order, created_by, created_at, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c04', '${seedTeamCode}', '${seedTeamName}',
       'Bootstrap team; can be removed once everyone has moved to the real structure',
       '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c01', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c03', 10, '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW(), '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

-- 5) Membership: both accounts, each with this as their primary team -----------
INSERT INTO team_members (id, user_id, team_id, is_primary_team)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c05', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c04', TRUE WHERE @is_empty = 0;
INSERT INTO team_members (id, user_id, team_id, is_primary_team)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c06', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c03', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c04', TRUE WHERE @is_empty = 0;

-- 6) Default configuration ----------------------------------------------------
INSERT INTO system_configs (id, config_key, config_value, description, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c07', 'reminder_send_hour', '9',
       'Hour of day the reminder job runs, 0-23, local time', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

INSERT INTO system_configs (id, config_key, config_value, description, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c08', 'reminder_enabled', 'true',
       'Master switch for all reminders', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

INSERT INTO system_configs (id, config_key, config_value, description, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c09', 'escalation_threshold_days', '3',
       'Days before a deadline at which reminders start escalating', '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

INSERT INTO system_configs (id, config_key, config_value, description, updated_by, updated_at)
SELECT '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c0a', 'approval_sla_days', '3',
       'Review service level; changing it applies only to assignments created afterwards',
       '018f2f5c-8d1e-7b3a-9c4d-1e2f3a4b5c02', NOW()
WHERE @is_empty = 0;

SET FOREIGN_KEY_CHECKS = 1;