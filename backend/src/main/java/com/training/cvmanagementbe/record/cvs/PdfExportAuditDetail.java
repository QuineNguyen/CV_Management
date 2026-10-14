package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.Language;

import java.util.UUID;

/*
 * new_value of an EXPORT_PDF audit row. With the CV as target, it pins down exactly what was sent
 * out - versions are immutable, so the same PDF can be rebuilt from these four values.
 */
public record PdfExportAuditDetail(
        UUID profileId,
        Language language,
        UUID versionId,
        int versionNumber
) {
}
