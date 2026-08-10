-- =============================================================================
-- V8 - Bootstrap Seed
--
-- Initializes baseline system entities for an empty database (Root Dept, Root Admin,
-- Root Tech Lead, Root Team, and System Configurations).
--
-- Disables FOREIGN_KEY_CHECKS temporarily to resolve circular references during seed execution.
-- =============================================================================

SET @is_empty = (SELECT COUNT(*) FROM users);

SET FOREIGN_KEY_CHECKS = 0;

-- 1) Root department ----------------------------------------------------------
INSERT INTO departments (id, code, name, parent_department_id, display_order,
                         created_by, created_at, updated_by, updated_at)
SELECT 1, '${seedDepartmentCode}', '${seedDepartmentName}', NULL, 10,
       1, NOW(), 1, NOW()
WHERE @is_empty = 0;

-- 2) Root administrator -------------------------------------------------------
INSERT INTO users (id, full_name, email, username, password_hash, role,
                   primary_department_id, status, must_change_password,
                   failed_login_count, token_valid_from,
                   created_by, created_at, updated_by, updated_at)
SELECT 1, 'Root administrator', '${seedAdminEmail}', '${seedAdminUsername}',
       '${seedPasswordHash}', 'ADMIN', 1, 'ACTIVE',
       TRUE,                 -- must choose a new password at first sign-in
       0, NOW(),
       1, NOW(), 1, NOW()    -- self-referencing: this is the first row in the system
WHERE @is_empty = 0;

-- 3) Root tech lead, needed so the root team has an active lead ----------------
INSERT INTO users (id, full_name, email, username, password_hash, role,
                   primary_department_id, status, must_change_password,
                   failed_login_count, token_valid_from,
                   created_by, created_at, updated_by, updated_at)
SELECT 2, 'Root tech lead', '${seedTechLeadEmail}', '${seedTechLeadUsername}',
       '${seedPasswordHash}', 'TECH_LEAD', 1, 'ACTIVE',
       TRUE, 0, NOW(),
       1, NOW(), 1, NOW()
WHERE @is_empty = 0;

-- 4) Root team ----------------------------------------------------------------
INSERT INTO teams (id, code, name, description, department_id, tech_lead_id,
                   display_order, created_by, created_at, updated_by, updated_at)
SELECT 1, '${seedTeamCode}', '${seedTeamName}',
       'Bootstrap team; can be removed once everyone has moved to the real structure',
       1, 2, 10, 1, NOW(), 1, NOW()
WHERE @is_empty = 0;

-- 5) Membership: both accounts, each with this as their primary team -----------
INSERT INTO team_members (user_id, team_id, is_primary_team)
SELECT 1, 1, TRUE WHERE @is_empty = 0;
INSERT INTO team_members (user_id, team_id, is_primary_team)
SELECT 2, 1, TRUE WHERE @is_empty = 0;

-- 6) Default configuration ----------------------------------------------------
INSERT INTO system_configs (config_key, config_value, description, updated_by, updated_at)
SELECT 'reminder_send_hour', '9',
       'Hour of day the reminder job runs, 0-23, local time', 1, NOW()
WHERE @is_empty = 0;

INSERT INTO system_configs (config_key, config_value, description, updated_by, updated_at)
SELECT 'reminder_enabled', 'true',
       'Master switch for all reminders', 1, NOW()
WHERE @is_empty = 0;

INSERT INTO system_configs (config_key, config_value, description, updated_by, updated_at)
SELECT 'escalation_threshold_days', '3',
       'Days before a deadline at which reminders start escalating', 1, NOW()
WHERE @is_empty = 0;

INSERT INTO system_configs (config_key, config_value, description, updated_by, updated_at)
SELECT 'approval_sla_days', '3',
       'Review service level; changing it applies only to assignments created afterwards',
       1, NOW()
WHERE @is_empty = 0;

SET FOREIGN_KEY_CHECKS = 1;

-- Because checking was off, the database cannot tell us whether any of this is consistent.
-- SeedBootstrapTest performs that verification once all migrations have run: it re-checks the
-- four references written while checking was disabled, and confirms both accounts satisfy the
-- team membership rules the loop above exists to work around.