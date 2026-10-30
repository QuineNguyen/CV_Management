package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.VersionSource;

import java.util.UUID;

/*
 * Everything VersionPublisher needs, from any of its three callers.
 *
 * - level1ApproverId: Null for DIRECT_EDIT and for APPROVAL when level 1 was skipped
 * - draftId: The draft this version closes; null for ROLLBACK and for a DIRECT_EDIT with no open draft
 */
public record PublishCommand(
        UUID cvId,
        CvContent content,
        UUID avatarImageId,
        VersionSource source,
        UUID authoredBy,
        UUID level1ApproverId,
        UUID level2ApproverId,
        UUID rollbackSourceVersionId,
        UUID draftId
) {

    // The owner editing their own CV: nobody else vouched for it, so both approvers stay null.
    public static PublishCommand directEdit(UUID cvId, CvContent content, UUID avatarImageId, UUID ownerId) {
        return directEdit(cvId, content, avatarImageId, ownerId, null);
    }

    /*
     * Same, closing the draft the owner was editing - for an Admin/HR owner that is the draft
     * structure sync created, which would otherwise stay open forever.
     */
    public static PublishCommand directEdit(UUID cvId, CvContent content, UUID avatarImageId,
                                            UUID ownerId, UUID draftId) {
        return new PublishCommand(cvId, content, avatarImageId, VersionSource.DIRECT_EDIT,
                ownerId, null, null, null, draftId);
    }

    public static PublishCommand approval(UUID cvId, CvContent content, UUID avatarImageId,
                                          UUID authoredBy, UUID level1ApproverId,
                                          UUID level2ApproverId, UUID draftId) {
        return new PublishCommand(cvId, content, avatarImageId, VersionSource.APPROVAL,
                authoredBy, level1ApproverId, level2ApproverId, null, draftId);
    }

    // Content, avatar and all three identity columns come from the source version
    public static PublishCommand rollback(UUID cvId, CvContent content, UUID avatarImageId,
                                          UUID authoredBy, UUID level1ApproverId,
                                          UUID level2ApproverId, UUID rollbackSourceVersionId) {
        return new PublishCommand(cvId, content, avatarImageId, VersionSource.ROLLBACK,
                authoredBy, level1ApproverId, level2ApproverId, rollbackSourceVersionId, null);
    }
}
