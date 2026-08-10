-- =============================================================================
-- V2 - CV core
-- image_files, skills, cv_profiles, cvs, cv_versions, change_log_entries, cv_drafts
-- =============================================================================

CREATE TABLE image_files (
                             id          UUID NOT NULL,
                             object_key  VARCHAR(512) NOT NULL,
                             uploaded_by UUID NOT NULL,
                             uploaded_at DATETIME NOT NULL,
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_image_files_object_key (object_key),
                             CONSTRAINT fk_image_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_image_id)
        REFERENCES image_files (id) ON DELETE SET NULL;

CREATE TABLE skills (
                        id            UUID NOT NULL,
                        code          VARCHAR(50)  NOT NULL,
                        name          VARCHAR(255) NOT NULL,
                        display_order INT NOT NULL DEFAULT 0,
                        created_by    UUID NOT NULL,
                        created_at    DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_skills_code (code),
                        UNIQUE KEY uk_skills_name (name),
                        KEY ix_skills_order (display_order, name),
                        CONSTRAINT fk_skills_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cv_profiles (
                             id               UUID NOT NULL,
                             employee_id      UUID NOT NULL,
                             name             VARCHAR(255) NOT NULL,
                             description      TEXT NULL,
                             is_primary       BOOLEAN NOT NULL DEFAULT FALSE,
                             linked_team_id   UUID NOT NULL,
                             lifecycle_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                             deleted_by       UUID NULL,
                             deleted_at       DATETIME NULL,
                             created_by       UUID NOT NULL,
                             created_at       DATETIME NOT NULL,
                             updated_by       UUID NOT NULL,
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

CREATE TABLE cvs (
                     id               UUID NOT NULL,
                     profile_id       UUID NOT NULL,
                     language         VARCHAR(40) NOT NULL,
                     master_cv_id     UUID NULL,
                     lifecycle_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                     deleted_by       UUID NULL,
                     deleted_at       DATETIME NULL,
                     created_by       UUID NOT NULL,
                     created_at       DATETIME NOT NULL,
                     updated_by       UUID NOT NULL,
                     updated_at       DATETIME NOT NULL,
                     PRIMARY KEY (id),
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

CREATE TABLE cv_versions (
                             id                         UUID NOT NULL,
                             cv_id                      UUID NOT NULL,
                             version_number             INT NOT NULL,
                             content_json               LONGTEXT NOT NULL,
                             avatar_image_id            UUID NULL,
                             authored_by                UUID NOT NULL,
                             level1_approver_id         UUID NULL,
                             level2_approver_id         UUID NULL,
                             published_at               DATETIME NOT NULL,
                             source                     VARCHAR(40) NOT NULL,
                             rollback_source_version_id UUID NULL,
                             change_summary             TEXT NULL,
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_cv_versions_number (cv_id, version_number),
                             KEY ix_cv_versions_avatar (avatar_image_id),
                             CONSTRAINT ck_cv_versions_source  CHECK (source IN ('APPROVAL','DIRECT_EDIT','ROLLBACK')),
                             CONSTRAINT ck_cv_versions_content CHECK (JSON_VALID(content_json)),
                             CONSTRAINT fk_cv_versions_cv       FOREIGN KEY (cv_id)      REFERENCES cvs (id),
                             CONSTRAINT fk_cv_versions_avatar   FOREIGN KEY (avatar_image_id)
                                 REFERENCES image_files (id) ON DELETE RESTRICT,
                             CONSTRAINT fk_cv_versions_authored FOREIGN KEY (authored_by)        REFERENCES users (id),
                             CONSTRAINT fk_cv_versions_lvl1     FOREIGN KEY (level1_approver_id) REFERENCES users (id),
                             CONSTRAINT fk_cv_versions_lvl2     FOREIGN KEY (level2_approver_id) REFERENCES users (id),
                             CONSTRAINT fk_cv_versions_rollback FOREIGN KEY (rollback_source_version_id)
                                 REFERENCES cv_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE change_log_entries (
                                    id          UUID NOT NULL,
                                    version_id  UUID NOT NULL,
                                    change_type VARCHAR(40) NOT NULL,
                                    section_key VARCHAR(40) NOT NULL,
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
                           id                    UUID NOT NULL,
                           cv_id                 UUID NOT NULL,
                           content_json          LONGTEXT NOT NULL,
                           avatar_image_id       UUID NULL,
                           status                VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
                           review_round          SMALLINT NOT NULL DEFAULT 0,
                           submitted_at          DATETIME NULL,
                           owner_id              UUID NOT NULL,
                           last_rejection_reason TEXT NULL,
                           published_version_id  UUID NULL,
                           cancellation_reason   TEXT NULL,
                           created_by            UUID NOT NULL,
                           created_at            DATETIME NOT NULL,
                           updated_by            UUID NOT NULL,
                           updated_at            DATETIME NOT NULL,
                           PRIMARY KEY (id),
                           KEY ix_cv_drafts_cv (cv_id, status),
                           KEY ix_cv_drafts_owner (owner_id, status),
                           CONSTRAINT ck_cv_drafts_status CHECK (status IN
                                                                 ('DRAFT','PENDING_TECH_LEAD','PENDING_HR','REJECTED','PUBLISHED','CANCELLED')),
                           CONSTRAINT ck_cv_drafts_content CHECK (JSON_VALID(content_json)),
                           CONSTRAINT fk_cv_drafts_cv         FOREIGN KEY (cv_id)    REFERENCES cvs (id),
                           CONSTRAINT fk_cv_drafts_avatar     FOREIGN KEY (avatar_image_id)
                               REFERENCES image_files (id) ON DELETE SET NULL,
                           CONSTRAINT fk_cv_drafts_owner      FOREIGN KEY (owner_id) REFERENCES users (id),
                           CONSTRAINT fk_cv_drafts_version    FOREIGN KEY (published_version_id) REFERENCES cv_versions (id),
                           CONSTRAINT fk_cv_drafts_created_by FOREIGN KEY (created_by) REFERENCES users (id),
                           CONSTRAINT fk_cv_drafts_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;