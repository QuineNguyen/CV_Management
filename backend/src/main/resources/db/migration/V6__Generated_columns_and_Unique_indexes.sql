-- =============================================================================
-- V6 - The eight "at most one row matching a condition" constraints
-- =============================================================================

-- 1. One primary profile per employee.
--    Zero is also valid - an employee who has not created a profile yet.
ALTER TABLE cv_profiles
    ADD COLUMN uk_active_primary UUID
        AS (CASE WHEN is_primary = TRUE AND lifecycle_status = 'ACTIVE'
                     THEN employee_id END) PERSISTENT,
    ADD UNIQUE KEY uk_cv_profiles_active_primary (uk_active_primary);

-- 2. Profile names are unique per employee.
--    Deleted profiles do not hold their name, which is what allows the flow where restoring a
--    profile whose name has since been reused asks for a new one.
ALTER TABLE cv_profiles
    ADD COLUMN uk_active_name VARCHAR(300)
        AS (CASE WHEN lifecycle_status = 'ACTIVE'
                     THEN CONCAT(employee_id, ':', name) END) PERSISTENT,
    ADD UNIQUE KEY uk_cv_profiles_active_name (uk_active_name);

-- 3. One active CV per profile and language.
--    Again limited to active rows, so deleting a CV genuinely frees the slot.
ALTER TABLE cvs
    ADD COLUMN uk_active_profile_lang VARCHAR(80)
        AS (CASE WHEN lifecycle_status = 'ACTIVE'
                     THEN CONCAT(profile_id, ':', language) END) PERSISTENT,
    ADD UNIQUE KEY uk_cvs_active_profile_lang (uk_active_profile_lang);

-- 4. One master CV per profile.
--    "Master" means having no parent CV. Because mastership is derived rather than stored in a
--    separate flag, there is no second column that could disagree with this one.
ALTER TABLE cvs
    ADD COLUMN uk_active_master UUID
        AS (CASE WHEN master_cv_id IS NULL AND lifecycle_status = 'ACTIVE'
                     THEN profile_id END) PERSISTENT,
    ADD UNIQUE KEY uk_cvs_active_master (uk_active_master);

-- 5. One open draft per CV.
--    Published and cancelled are terminal and deliberately excluded, so an owner who cancels a
--    draft can immediately start another.
ALTER TABLE cv_drafts
    ADD COLUMN uk_open_draft UUID
        AS (CASE WHEN status IN ('DRAFT','PENDING_TECH_LEAD','PENDING_HR','REJECTED')
                     THEN cv_id END) PERSISTENT,
    ADD UNIQUE KEY uk_cv_drafts_open (uk_open_draft);

-- 6. One open approval assignment per draft.
--    This is what makes the handover chain reconstructable from assignment times alone, and so
--    what removes the need for a "next assignment" pointer.
ALTER TABLE approval_assignments
    ADD COLUMN uk_assigned_draft UUID
        AS (CASE WHEN status = 'ASSIGNED' THEN draft_id END) PERSISTENT,
    ADD UNIQUE KEY uk_approval_assignments_assigned (uk_assigned_draft);

-- 7. One pending request per employee, profile and language.
--    COALESCE is the whole point here. A request that does not yet target a profile has a NULL
--    profile_id, and NULLs do not collide in a unique index - without the coalesce, the same
--    request could be created any number of times and the employee would receive repeated
--    emails demanding the same thing. Zero UUID is safe as the sentinel because profile ids are
--    generated UUIDs.
ALTER TABLE update_requests
    ADD COLUMN uk_pending_key VARCHAR(120)
        AS (CASE WHEN status = 'PENDING'
                     THEN CONCAT(employee_id, ':', COALESCE(profile_id, '00000000-0000-0000-0000-000000000000'), ':', language) END) PERSISTENT,
    ADD UNIQUE KEY uk_update_requests_pending (uk_pending_key);

-- 8. One reminder per target, recipient and day.
--    A plain unique key, because every column is already NOT NULL - reminders with no specific
--    target point at the recipient rather than storing NULL, for the reason given in 7.
ALTER TABLE reminder_logs
    ADD UNIQUE KEY uk_reminder_logs_daily (target_type, target_id, recipient_id, sent_date);