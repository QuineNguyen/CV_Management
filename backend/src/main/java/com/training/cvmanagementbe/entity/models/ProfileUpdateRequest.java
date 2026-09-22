package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.profile_updates.ProfileUpdateStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * A snapshot of what a user asked to change about their own record.
 *
 * - user_id is always the account owner - there is no submit-on-behalf-of path, which is why
 * there is no separate business-level submitter column (symmetric with cv_drafts.owner_id).
 * - The requester's role is deliberately not snapshot here: routing reads users.role at
 * decision time and a copy would drift the moment an Admin changed someone's role mid-request.
 * - pending_slot is a virtual-generated column in the DB, not mapped here: nothing reads it in
 * Java and mapping a generated column in the DB, not mapped: nothing in Java reads it.
 */
@Entity
@Table(name = "profile_update_requests")
@Getter
@Setter
public class ProfileUpdateRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProfileUpdateStatus status = ProfileUpdateStatus.PENDING;

    // ---------- Requested values ----------

    @Column(name = "requested_full_name", length = 200)
    private String requestedFullName;

    @Column(name = "requested_date_of_birth")
    private LocalDate requestedDateOfBirth;

    @Column(name = "requested_phone_number", length = 30)
    private String requestedPhoneNumber;

    @Column(name = "requested_address", length = 500)
    private String requestedAddress;

    @Column(name = "requested_avatar_image_id")
    private UUID requestedAvatarImageId;

    // ---------- Review outcome ----------

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    public boolean isPending() {
        return status == ProfileUpdateStatus.PENDING;
    }
}
