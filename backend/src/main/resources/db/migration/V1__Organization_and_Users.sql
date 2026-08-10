-- =============================================================================
-- V1 - Organisation and users
-- departments, users, external_account_links, teams, team_members
-- =============================================================================

CREATE TABLE departments (
                             id                    UUID NOT NULL,
                             code                  VARCHAR(50)  NOT NULL,
                             name                  VARCHAR(255) NOT NULL,
                             parent_department_id  UUID NULL,
                             display_order         INT NOT NULL DEFAULT 0,
                             created_by            UUID NOT NULL,
                             created_at            DATETIME NOT NULL,
                             updated_by            UUID NOT NULL,
                             updated_at            DATETIME NOT NULL,
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_departments_code (code),
                             UNIQUE KEY uk_departments_name (name),
                             KEY ix_departments_parent (parent_department_id, display_order),
                             CONSTRAINT fk_departments_parent FOREIGN KEY (parent_department_id)
                                 REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
                       id                    UUID NOT NULL,
                       full_name             VARCHAR(255) NOT NULL,
                       email                 VARCHAR(255) NOT NULL,
                       username              VARCHAR(255) NOT NULL,
                       password_hash         VARCHAR(255) NOT NULL,
                       role                  VARCHAR(40)  NOT NULL,
                       primary_department_id UUID NOT NULL,
                       status                VARCHAR(40)  NOT NULL,
                       date_of_birth         DATE NULL,
                       phone_number          VARCHAR(50) NULL,
                       address               VARCHAR(255) NULL,
                       avatar_image_id       UUID NULL,
                       must_change_password  BOOLEAN NOT NULL DEFAULT FALSE,
                       failed_login_count    INT NOT NULL DEFAULT 0,
                       locked_until          DATETIME NULL,
                       token_valid_from      DATETIME NOT NULL,
                       created_by            UUID NOT NULL,
                       created_at            DATETIME NOT NULL,
                       updated_by            UUID NOT NULL,
                       updated_at            DATETIME NOT NULL,
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_users_email (email),
                       UNIQUE KEY uk_users_username (username),
                       KEY ix_users_role_status (role, status),
                       KEY ix_users_department (primary_department_id),
                       CONSTRAINT ck_users_role   CHECK (role IN ('ADMIN','HR','TECH_LEAD','EMPLOYEE')),
                       CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE')),
                       CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id),
                       CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE users
    ADD CONSTRAINT fk_users_department FOREIGN KEY (primary_department_id)
        REFERENCES departments (id);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_departments_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

CREATE TABLE external_account_links (
                                        id               UUID NOT NULL,
                                        user_id          UUID NOT NULL,
                                        provider         VARCHAR(40)  NOT NULL DEFAULT 'GOOGLE',
                                        provider_user_id VARCHAR(255) NOT NULL,
                                        provider_email   VARCHAR(255) NOT NULL,
                                        linked_at        DATETIME NOT NULL,
                                        PRIMARY KEY (id),
                                        UNIQUE KEY uk_ext_links_user (user_id),
                                        UNIQUE KEY uk_ext_links_provider (provider, provider_user_id),
                                        CONSTRAINT ck_ext_links_provider CHECK (provider IN ('GOOGLE')),
                                        CONSTRAINT fk_ext_links_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teams (
                       id            UUID NOT NULL,
                       code          VARCHAR(50)  NOT NULL,
                       name          VARCHAR(255) NOT NULL,
                       description   TEXT NULL,
                       department_id UUID NOT NULL,
                       tech_lead_id  UUID NOT NULL,
                       display_order INT NOT NULL DEFAULT 0,
                       created_by    UUID NOT NULL,
                       created_at    DATETIME NOT NULL,
                       updated_by    UUID NOT NULL,
                       updated_at    DATETIME NOT NULL,
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_teams_code (code),
                       KEY ix_teams_department (department_id, display_order),
                       KEY ix_teams_tech_lead (tech_lead_id),
                       CONSTRAINT fk_teams_department FOREIGN KEY (department_id) REFERENCES departments (id),
                       CONSTRAINT fk_teams_tech_lead  FOREIGN KEY (tech_lead_id)  REFERENCES users (id),
                       CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by)    REFERENCES users (id),
                       CONSTRAINT fk_teams_updated_by FOREIGN KEY (updated_by)    REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_members (
                              id              UUID NOT NULL,
                              user_id         UUID NOT NULL,
                              team_id         UUID NOT NULL,
                              is_primary_team BOOLEAN NOT NULL DEFAULT FALSE,
                              PRIMARY KEY (id),
                              UNIQUE KEY uk_team_members_pair (user_id, team_id),
                              KEY ix_team_members_team (team_id),
                              CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users (id),
                              CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;