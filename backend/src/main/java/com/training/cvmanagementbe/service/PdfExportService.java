package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.response.cvs.pdf.CvPdfFile;
import com.training.cvmanagementbe.dto.response.cvs.pdf.CvPdfLinkResponse;

import java.util.UUID;

public interface PdfExportService {

    /*
     * Renders one published version of an ACTIVE CV with the standard 9-section template.
     * Drafts are never exportable: Only cv_versions rows are read. Same read scope as the CV itself.
     */
    CvPdfFile export(UUID cvId, UUID versionId);

    /*
     * Issues a short-lived signed link to the same PDF, for an iframe or a browser tab - neither can
     * send the JWT header. Scope is checked and the export is audited here, when the link is issued.
     */
    CvPdfLinkResponse createLink(UUID cvId, UUID versionId);

    // Renders the PDF a signed link points at; an expired or forged token reads as not found
    CvPdfFile openLink(String token);
}
