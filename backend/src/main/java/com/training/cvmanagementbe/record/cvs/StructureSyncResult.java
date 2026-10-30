package com.training.cvmanagementbe.record.cvs;

import com.fasterxml.jackson.annotation.JsonIgnore;

/*
 * Outcome of applying a master's structure change to one target CV.
 *
 * - content: The target after the change; unchanged input when changed() is false.
 * - The counts double as the audit entry, so content is kept out of the JSON.
 */
public record StructureSyncResult(
        @JsonIgnore CvContent content,
        // Placeholders inserted for entries new in the master
        int addedCount,
        // Entries whose removed-in-master flag was cleared because the master has them again.
        int restoredCount,
        // Entries newly flagged as removed in the master
        int flaggedCount,
        // Sections whose entry order moved
        int reorderedSectionCount
) {

    // False means write nothing, create no draft and notify nobody.
    public boolean changed() {
        return addedCount + restoredCount + flaggedCount + reorderedSectionCount > 0;
    }
}
