package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.DraftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/*
 * The employee's workspace and the object that travels through the approval flow.
 *
 * - Approvals, decisions and inline comments all attach to the draft, never to a version.
 * Content is writable only in DRAFT / REJECTED - a draft under review is
 * read-only for everyone including its owner, so reviewers decide on what they actually read.
 */
@Entity
@Table(name = "cv_drafts")
@Getter
@Setter
public class CvDraft extends BaseEntity {

    // Statuses that count as "open"; PUBLISHED and CANCELLED are terminal.
    public static final Set<DraftStatus> OPEN_STATUSES = Set.of(
            DraftStatus.DRAFT,
            DraftStatus.PENDING_TECH_LEAD,
            DraftStatus.PENDING_HR,
            DraftStatus.REJECTED
    );

    // Statuses in which the content is frozen for review.
    public static final Set<DraftStatus> LOCKED_STATUSES = Set.of(
            DraftStatus.PENDING_TECH_LEAD,
            DraftStatus.PENDING_HR
    );

    @Column(name = "cv_id", nullable = false)
    private UUID cvId;

    @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT")
    private String contentJson;

    @Column(name = "avatar_image_id")
    private UUID avatarImageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DraftStatus status = DraftStatus.DRAFT;

    // +1 on every submit; scopes inline comments to a round.
    @Column(name = "review_round", nullable = false, columnDefinition = "SMALLINT")
    private int reviewRound;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // The CV owner, who is also the author and the submitter.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "last_rejection_reason", columnDefinition = "TEXT")
    private String lastRejectionReason;

    @Column(name = "published_version_id")
    private UUID publishedVersionId;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Transient
    public boolean isContentLocked() {
        return LOCKED_STATUSES.contains(status);
    }
}
