package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.VersionSource;

import java.util.UUID;

/*
 * Everything VersionPublisher needs, from any of its three callers.
 *
 * - level1ApproverId: Null for DIRECT_EDIT and for APPROVAL when level 1 was skipped
 * - draftId: Null when no draft was involved (DIRECT_EDIT, ROLLBACK)
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
        return new PublishCommand(cvId, content, avatarImageId, VersionSource.DIRECT_EDIT,
                ownerId, null, null, null, null);
    }

    public static PublishCommand approval(UUID cvId, CvContent content, UUID avatarImageId,
                                          UUID authoredBy, UUID level1ApproverId,
                                          UUID level2ApproverId, UUID draftId) {
        return new PublishCommand(cvId, content, avatarImageId, VersionSource.APPROVAL,
                authoredBy, level1ApproverId, level2ApproverId, null, draftId);
    }
}
