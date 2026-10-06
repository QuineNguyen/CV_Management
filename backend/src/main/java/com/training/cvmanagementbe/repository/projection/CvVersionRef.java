package com.training.cvmanagementbe.repository.projection;

import com.training.cvmanagementbe.enums.cvs.VersionSource;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Version columns without content_json / content_text (LONGTEXT).
 * - Rows of the history timeline.
 * - id <-> version number lookups for predecessors and rollback sources.
 */
public interface CvVersionRef {

    UUID getId();

    int getVersionNumber();

    LocalDateTime getPublishedAt();

    VersionSource getSource();

    UUID getAuthoredBy();

    UUID getLevel1ApproverId();

    UUID getLevel2ApproverId();

    UUID getRollbackSourceVersionId();
}
