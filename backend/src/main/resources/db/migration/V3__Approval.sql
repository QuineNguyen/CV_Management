-- =============================================================================
-- V3 - Approval
-- approval_assignments, approval_decisions, inline_comments
-- =============================================================================

CREATE TABLE approval_assignments (
                                      id           UUID NOT NULL,
                                      draft_id     UUID NOT NULL,
                                      level        SMALLINT NOT NULL,       -- 1 = team lead, 2 = HR
                                      assignee_id  UUID NULL,
                                      assigned_by  UUID NULL,
                                      review_round SMALLINT NOT NULL,
                                      status       VARCHAR(40) NOT NULL DEFAULT 'ASSIGNED',
                                      reason       TEXT NOT NULL,
                                      assigned_at  DATETIME NOT NULL,
                                      due_at       DATETIME NOT NULL,
                                      closed_at    DATETIME NULL,
                                      PRIMARY KEY (id),
                                      KEY ix_appr_assign_draft (draft_id, level, review_round),
                                      KEY ix_appr_assign_assignee (assignee_id, status),
                                      KEY ix_appr_assign_due (status, due_at),
                                      CONSTRAINT ck_appr_assign_level  CHECK (level IN (1,2)),
                                      CONSTRAINT ck_appr_assign_status CHECK (status IN
                                                                              ('ASSIGNED','COMPLETED','REASSIGNED','SKIPPED','CANCELLED')),
                                      CONSTRAINT fk_appr_assign_draft    FOREIGN KEY (draft_id)    REFERENCES cv_drafts (id),
                                      CONSTRAINT fk_appr_assign_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
                                      CONSTRAINT fk_appr_assign_by       FOREIGN KEY (assigned_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE approval_decisions (
                                    id           UUID NOT NULL,
                                    draft_id     UUID NOT NULL,
                                    level        SMALLINT NOT NULL,
                                    review_round SMALLINT NOT NULL,
                                    approver_id  UUID NULL,
                                    result       VARCHAR(40) NOT NULL,
                                    reason       TEXT NULL,
                                    decided_at   DATETIME NOT NULL,
                                    PRIMARY KEY (id),
                                    KEY ix_appr_decision_draft (draft_id, level, review_round),
                                    CONSTRAINT ck_appr_decision_level  CHECK (level IN (1,2)),
                                    CONSTRAINT ck_appr_decision_result CHECK (result IN ('APPROVED','REJECTED','SKIPPED')),
                                    CONSTRAINT fk_appr_decision_draft    FOREIGN KEY (draft_id)    REFERENCES cv_drafts (id),
                                    CONSTRAINT fk_appr_decision_approver FOREIGN KEY (approver_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inline_comments (
                                 id                UUID NOT NULL,
                                 draft_id          UUID NOT NULL,
                                 review_round      SMALLINT NOT NULL,
                                 section_key       VARCHAR(40) NOT NULL,
                                 item_id           VARCHAR(64) NULL,
                                 field_key         VARCHAR(64) NULL,
                                 author_id         UUID NOT NULL,
                                 content           TEXT NOT NULL,
                                 status            VARCHAR(40) NOT NULL DEFAULT 'OPEN',
                                 parent_comment_id UUID NULL,
                                 created_at        DATETIME NOT NULL,
                                 PRIMARY KEY (id),
                                 KEY ix_inline_comments_draft (draft_id, review_round, status),
                                 KEY ix_inline_comments_parent (parent_comment_id),
                                 CONSTRAINT ck_inline_comments_status CHECK (status IN ('OPEN','RESOLVED')),
                                 CONSTRAINT ck_inline_comments_section CHECK (section_key IN
                                                                              ('personal_info','career_objective','skills','experience','education',
                                                                               'certifications','projects','languages','additional_info')),
                                 CONSTRAINT fk_inline_comments_draft  FOREIGN KEY (draft_id)  REFERENCES cv_drafts (id),
                                 CONSTRAINT fk_inline_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
                                 CONSTRAINT fk_inline_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES inline_comments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;