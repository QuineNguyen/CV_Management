-- =============================================================================
-- V9 - Drop the department <-> team relationship
-- A project draws its members from several departments, so a team has no
-- owning department. Nothing reads the column: level-1 approvers resolve through
-- cv_profiles.linked_team_id -> teams.tech_lead_id, Tech Lead data scope through
-- team_members, and every "by department" figure through users.primary_department_id.
-- Employee <-> department is untouched: it lives on users.primary_department_id.
-- =============================================================================

ALTER TABLE teams DROP FOREIGN KEY fk_teams_department;
ALTER TABLE teams DROP INDEX ix_teams_department;
ALTER TABLE teams DROP COLUMN department_id;
