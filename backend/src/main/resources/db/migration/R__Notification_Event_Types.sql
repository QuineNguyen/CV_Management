-- =============================================================================
-- Repeatable: allowed values of notifications.type and email_logs.event_type.
-- Mirrors NotificationEventType. Flyway re-runs this file whenever it changes,
-- so a new event type is one line in each list here - never a new V migration.
-- Both lists must stay identical and a superset of every stored value:
-- ADD CONSTRAINT re-checks existing rows.
-- IF EXISTS keeps a re-run safe after a half-applied attempt (DDL is not transactional).
-- =============================================================================

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
         'DRAFT_SUBMITTED', 'DRAFT_APPROVED_LEVEL_1', 'DRAFT_APPROVED_LEVEL_2', 'DRAFT_REJECTED',
         'DRAFT_CANCELLED', 'ASSIGNMENT_REASSIGNED', 'CV_DELETED', 'CV_RESTORED', 'CV_ROLLBACK',
         'CV_PROFILE_DELETED', 'CV_UPDATE_REQUESTED', 'CV_UPDATE_REQUEST_CANCELLED', 'PROFILE_UPDATE_SUBMITTED',
         'PROFILE_UPDATE_DECIDED', 'PASSWORD_RESET', 'ACCOUNT_CREATED', 'TEAM_HANDOVER',
         'REMINDER_UPDATE_REQUEST','REMINDER_APPROVAL_ASSIGNMENT','SLA_DIGEST'
    ));

ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS ck_email_logs_event_type;

ALTER TABLE email_logs
    ADD CONSTRAINT ck_email_logs_event_type CHECK (event_type IN (
          'DRAFT_SUBMITTED', 'DRAFT_APPROVED_LEVEL_1', 'DRAFT_APPROVED_LEVEL_2', 'DRAFT_REJECTED',
          'DRAFT_CANCELLED', 'ASSIGNMENT_REASSIGNED', 'CV_DELETED', 'CV_RESTORED', 'CV_ROLLBACK',
          'CV_PROFILE_DELETED', 'CV_UPDATE_REQUESTED', 'CV_UPDATE_REQUEST_CANCELLED', 'PROFILE_UPDATE_SUBMITTED',
          'PROFILE_UPDATE_DECIDED', 'PASSWORD_RESET', 'ACCOUNT_CREATED', 'TEAM_HANDOVER',
          'REMINDER_UPDATE_REQUEST','REMINDER_APPROVAL_ASSIGNMENT','SLA_DIGEST'
    ));