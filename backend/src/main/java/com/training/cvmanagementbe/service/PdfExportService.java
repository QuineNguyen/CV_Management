package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.response.cvs.pdf.CvPdfFile;

import java.util.UUID;

public interface PdfExportService {

    /*
     * Renders one published version of an ACTIVE CV with the standard 9-section template.
     * Drafts are never exportable: Only cv_versions rows are read. Same read scope as the CV itself.
     */
    CvPdfFile export(UUID cvId, UUID versionId);
}
