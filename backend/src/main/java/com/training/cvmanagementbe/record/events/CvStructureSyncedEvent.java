package com.training.cvmanagementbe.record.events;

import com.training.cvmanagementbe.enums.cvs.Language;

import java.util.UUID;

/*
 * Structure sync wrote into one target draft. Published inside the master's publishing
 * transaction and handled after commit, so a rolled-back publish notifies nobody.
 *
 * - untranslatedCount / deletedInMasterCount: Totals of the draft after the sync.
 * - assigneeId: The reviewer holding the draft when it is PENDING_*; null otherwise.
 */
public record CvStructureSyncedEvent(
        UUID cvId,
        UUID draftId,
        UUID ownerId,
        Language language,
        long untranslatedCount,
        long deletedInMasterCount,
        UUID assigneeId
) {
}
