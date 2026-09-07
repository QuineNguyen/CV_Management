package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.VersionSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * An immutable published snapshot: INSERT only, never UPDATE, never DELETE.
 *
 * - Every column is updatable = false so that an accidental (save()) on a managed
 * instance cannot rewrite a version already sent to a customer. The entity deliberately does not
 * extend BaseEntity - "last modified by" is meaningless for a row that is never modified.
 */
@Entity
@Table(name = "cv_versions")
@Getter
@Setter
public class CvVersion {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cv_id", updatable = false, nullable = false)
    private UUID cvId;

    // MAX(existing) + 1 within one CV; restarts at 1 for a CV of a new profile.
    @Column(name = "version_number", updatable = false, nullable = false)
    private int versionNumber;

    // Full snapshot, never a delta. Holds no image reference.
    @Column(name = "content_json", updatable = false, nullable = false, columnDefinition = "LONGTEXT")
    private String contentJson;

    // Flattened text of contentJson, for full-text search over CV content.
    @Column(name = "content_text", updatable = false, columnDefinition = "LONGTEXT")
    private String contentText;

    // FK to image_files, ON DELETE RESTRICT.
    @Column(name = "avatar_image_id", updatable = false)
    private UUID avatarImageId;

    // Always the CV owner. For ROLLBACK it is copied from the source version.
    @Column(name = "authored_by", updatable = false, nullable = false)
    private UUID authoredBy;

    // Null when the owner published directly, or when level 1 was skipped as the submitter.
    @Column(name = "level1_approver_id", updatable = false)
    private UUID level1ApproverId;

    @Column(name = "level2_approver_id", updatable = false)
    private UUID level2ApproverId;

    @Column(name = "published_at", updatable = false, nullable = false)
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", updatable = false, nullable = false, length = 40)
    private VersionSource source;

    @Column(name = "rollback_source_version_id", updatable = false)
    private UUID rollbackSourceVersionId;

    @Column(name = "change_summary", updatable = false, columnDefinition = "TEXT")
    private String changeSummary;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
