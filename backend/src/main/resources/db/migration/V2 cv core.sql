-- =============================================================================
-- V2 - CV core
-- image_files, skills, cv_profiles, cvs, cv_versions, change_log_entries, cv_drafts
-- =============================================================================

CREATE TABLE image_files (
                             id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- Key in the object store. Image bytes never enter the database; only the reference does.
                             object_key  VARCHAR(512) NOT NULL,
                             uploaded_by BIGINT UNSIGNED NOT NULL,
                             uploaded_at DATETIME NOT NULL,
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_image_files_object_key (object_key),
                             CONSTRAINT fk_image_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A profile picture can be replaced at any time, so losing the image just clears the field.
ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_image_id)
        REFERENCES image_files (id) ON DELETE SET NULL;

CREATE TABLE skills (
                        id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        code          VARCHAR(50)  NOT NULL,
                        name          VARCHAR(255) NOT NULL,
                        display_order INT NOT NULL DEFAULT 0,
    -- Employees may add a skill while filling in their CV rather than waiting for an
    -- administrator, so this is not restricted to privileged accounts.
                        created_by    BIGINT UNSIGNED NOT NULL,
                        created_at    DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_skills_code (code),
                        UNIQUE KEY uk_skills_name (name),
                        KEY ix_skills_order (display_order, name),
                        CONSTRAINT fk_skills_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Rows are never deleted from this table. Immutable published versions reference skills from
-- inside their JSON content, where no foreign key can protect the reference: deleting a row
-- would leave those versions pointing at nothing, with nothing to have stopped it.

CREATE TABLE cv_profiles (
                             id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             employee_id      BIGINT UNSIGNED NOT NULL,
                             name             VARCHAR(255) NOT NULL,
                             description      TEXT NULL,
                             is_primary       BOOLEAN NOT NULL DEFAULT FALSE,
    -- Required. A profile drives approval routing, and routing needs a team to resolve the
    -- reviewer from; a profile without one would have no route.
                             linked_team_id   BIGINT UNSIGNED NOT NULL,
                             lifecycle_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                             deleted_by       BIGINT UNSIGNED NULL,
                             deleted_at       DATETIME NULL,
                             created_by       BIGINT UNSIGNED NOT NULL,
                             created_at       DATETIME NOT NULL,
                             updated_by       BIGINT UNSIGNED NOT NULL,
                             updated_at       DATETIME NOT NULL,
                             PRIMARY KEY (id),
                             KEY ix_cv_profiles_employee (employee_id, lifecycle_status),
                             KEY ix_cv_profiles_team (linked_team_id),
                             CONSTRAINT ck_cv_profiles_lifecycle CHECK (lifecycle_status IN ('ACTIVE','DELETED')),
                             CONSTRAINT fk_cv_profiles_employee   FOREIGN KEY (employee_id)    REFERENCES users (id),
                             CONSTRAINT fk_cv_profiles_team       FOREIGN KEY (linked_team_id) REFERENCES teams (id),
                             CONSTRAINT fk_cv_profiles_deleted_by FOREIGN KEY (deleted_by)     REFERENCES users (id),
                             CONSTRAINT fk_cv_profiles_created_by FOREIGN KEY (created_by)     REFERENCES users (id),
                             CONSTRAINT fk_cv_profiles_updated_by FOREIGN KEY (updated_by)     REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Unique name per employee and a single primary profile, both limited to active rows: see V6.

CREATE TABLE cvs (
                     id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                     profile_id       BIGINT UNSIGNED NOT NULL,
                     language         VARCHAR(40) NOT NULL,
    -- Empty means this CV *is* the master. Deriving mastership from the absence of a parent
    -- rather than from a separate flag removes any way for the two to disagree.
                     master_cv_id     BIGINT UNSIGNED NULL,
                     lifecycle_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                     deleted_by       BIGINT UNSIGNED NULL,
                     deleted_at       DATETIME NULL,
                     created_by       BIGINT UNSIGNED NOT NULL,
                     created_at       DATETIME NOT NULL,
                     updated_by       BIGINT UNSIGNED NOT NULL,
                     updated_at       DATETIME NOT NULL,
                     PRIMARY KEY (id),
    -- Redundant as a uniqueness rule, since id alone is already unique. It exists because a
    -- composite foreign key needs a key of exactly this shape to point at; see V4.
                     UNIQUE KEY uk_cvs_id_profile (id, profile_id),
                     KEY ix_cvs_profile (profile_id, lifecycle_status),
                     KEY ix_cvs_master (master_cv_id),
                     CONSTRAINT ck_cvs_language  CHECK (language IN ('VI','EN','JA')),
  CONSTRAINT ck_cvs_lifecycle CHECK (lifecycle_status IN ('ACTIVE','DELETED')),
  CONSTRAINT fk_cvs_profile    FOREIGN KEY (profile_id)   REFERENCES cv_profiles (id),
  CONSTRAINT fk_cvs_master     FOREIGN KEY (master_cv_id) REFERENCES cvs (id),
  CONSTRAINT fk_cvs_deleted_by FOREIGN KEY (deleted_by)   REFERENCES users (id),
  CONSTRAINT fk_cvs_created_by FOREIGN KEY (created_by)   REFERENCES users (id),
  CONSTRAINT fk_cvs_updated_by FOREIGN KEY (updated_by)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- One active CV per language, and one master per profile: see V6.

-- Immutable once published: never updated, never deleted. Everything downstream depends on
-- this - the approval record, the change log, and rollback all describe a fixed snapshot.
CREATE TABLE cv_versions (
                             id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             cv_id                      BIGINT UNSIGNED NOT NULL,
                             version_number             INT NOT NULL,
                             content_json               LONGTEXT NOT NULL,
                             avatar_image_id            BIGINT UNSIGNED NULL,
    -- Always the CV's owner, including for a rollback, which inherits the authorship of the
    -- version it restores rather than crediting whoever pressed the button.
                             authored_by                BIGINT UNSIGNED NOT NULL,
                             level1_approver_id         BIGINT UNSIGNED NULL,
                             level2_approver_id         BIGINT UNSIGNED NULL,
                             published_at               DATETIME NOT NULL,
                             source                     VARCHAR(40) NOT NULL,
                             rollback_source_version_id BIGINT UNSIGNED NULL,
                             change_summary             TEXT NULL,
                             PRIMARY KEY (id),
    -- Also the index used to find the highest existing number when publishing.
                             UNIQUE KEY uk_cv_versions_number (cv_id, version_number),
                             KEY ix_cv_versions_avatar (avatar_image_id),
                             CONSTRAINT ck_cv_versions_source  CHECK (source IN ('APPROVAL','DIRECT_EDIT','ROLLBACK')),
                             CONSTRAINT ck_cv_versions_content CHECK (JSON_VALID(content_json)),
                             CONSTRAINT fk_cv_versions_cv       FOREIGN KEY (cv_id)      REFERENCES cvs (id),
    -- RESTRICT, so an image referenced by a published version cannot be deleted. The database
    -- enforces this directly instead of a background job scanning JSON for references.
                             CONSTRAINT fk_cv_versions_avatar   FOREIGN KEY (avatar_image_id)
                                 REFERENCES image_files (id) ON DELETE RESTRICT,
                             CONSTRAINT fk_cv_versions_authored FOREIGN KEY (authored_by)        REFERENCES users (id),
                             CONSTRAINT fk_cv_versions_lvl1     FOREIGN KEY (level1_approver_id) REFERENCES users (id),
                             CONSTRAINT fk_cv_versions_lvl2     FOREIGN KEY (level2_approver_id) REFERENCES users (id),
                             CONSTRAINT fk_cv_versions_rollback FOREIGN KEY (rollback_source_version_id)
                                 REFERENCES cv_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE change_log_entries (
                                    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                    version_id  BIGINT UNSIGNED NOT NULL,
                                    change_type VARCHAR(40) NOT NULL,
                                    section_key VARCHAR(40) NOT NULL,
    -- Empty for single-value sections. Same convention as inline comments and request notes;
    -- if the three drifted apart, the same coordinate would mean different things.
                                    item_id     VARCHAR(64) NULL,
                                    field_key   VARCHAR(64) NULL,
                                    old_value   TEXT NULL,
                                    new_value   TEXT NULL,
                                    PRIMARY KEY (id),
                                    KEY ix_change_log_version (version_id),
                                    CONSTRAINT ck_change_log_type CHECK (change_type IN ('ADDED','MODIFIED','REMOVED')),
                                    CONSTRAINT ck_change_log_section CHECK (section_key IN
                                                                            ('personal_info','career_objective','skills','experience','education',
                                                                             'certifications','projects','languages','additional_info')),
                                    CONSTRAINT fk_change_log_version FOREIGN KEY (version_id) REFERENCES cv_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cv_drafts (
                           id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                           cv_id                 BIGINT UNSIGNED NOT NULL,
    -- Writable only while the draft is being edited or has been sent back. Once it is awaiting
    -- a decision the content freezes, so reviewers decide on exactly what they read.
                           content_json          LONGTEXT NOT NULL,
                           avatar_image_id       BIGINT UNSIGNED NULL,
                           status                VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
                           review_round          SMALLINT NOT NULL DEFAULT 0,
                           submitted_at          DATETIME NULL,
                           owner_id              BIGINT UNSIGNED NOT NULL,
                           last_rejection_reason TEXT NULL,
                           published_version_id  BIGINT UNSIGNED NULL,
                           cancellation_reason   TEXT NULL,
                           created_by            BIGINT UNSIGNED NOT NULL,
                           created_at            DATETIME NOT NULL,
                           updated_by            BIGINT UNSIGNED NOT NULL,
                           updated_at            DATETIME NOT NULL,
                           PRIMARY KEY (id),
                           KEY ix_cv_drafts_cv (cv_id, status),
                           KEY ix_cv_drafts_owner (owner_id, status),
                           CONSTRAINT ck_cv_drafts_status CHECK (status IN
                                                                 ('DRAFT','PENDING_TECH_LEAD','PENDING_HR','REJECTED','PUBLISHED','CANCELLED')),
                           CONSTRAINT ck_cv_drafts_content CHECK (JSON_VALID(content_json)),
                           CONSTRAINT fk_cv_drafts_cv         FOREIGN KEY (cv_id)    REFERENCES cvs (id),
    -- A draft is still being worked on, so replacing its image is ordinary editing.
                           CONSTRAINT fk_cv_drafts_avatar     FOREIGN KEY (avatar_image_id)
                               REFERENCES image_files (id) ON DELETE SET NULL,
                           CONSTRAINT fk_cv_drafts_owner      FOREIGN KEY (owner_id) REFERENCES users (id),
                           CONSTRAINT fk_cv_drafts_version    FOREIGN KEY (published_version_id) REFERENCES cv_versions (id),
                           CONSTRAINT fk_cv_drafts_created_by FOREIGN KEY (created_by) REFERENCES users (id),
                           CONSTRAINT fk_cv_drafts_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- At most one open draft per CV: see V6.