-- =============================================================================
-- V11 - CV_UPDATE_REQUESTED joins the notification event types
-- notifications.type and email_logs.event_type share the value set of
-- NotificationEventType, stored by constant name.
-- Verify names and current lists first: SHOW CREATE TABLE notifications;
--                                       SHOW CREATE TABLE email_logs;
-- =============================================================================

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'DRAFT_SUBMITTED', 'DRAFT_APPROVED_LEVEL_1', 'DRAFT_APPROVED_LEVEL_2', 'DRAFT_REJECTED',
    'DRAFT_CANCELLED', 'ASSIGNMENT_REASSIGNED', 'CV_DELETED', 'CV_RESTORED', 'CV_PROFILE_DELETED',
    'CV_UPDATE_REQUESTED', 'PROFILE_UPDATE_SUBMITTED', 'PROFILE_UPDATE_DECIDED', 'PASSWORD_RESET'
));

ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS ck_email_logs_event_type;
ALTER TABLE email_logs ADD CONSTRAINT ck_email_logs_event_type CHECK (event_type IN (
     'DRAFT_SUBMITTED', 'DRAFT_APPROVED_LEVEL_1', 'DRAFT_APPROVED_LEVEL_2', 'DRAFT_REJECTED',
     'DRAFT_CANCELLED', 'ASSIGNMENT_REASSIGNED', 'CV_DELETED', 'CV_RESTORED', 'CV_PROFILE_DELETED',
     'CV_UPDATE_REQUESTED', 'PROFILE_UPDATE_SUBMITTED', 'PROFILE_UPDATE_DECIDED', 'PASSWORD_RESET'
));