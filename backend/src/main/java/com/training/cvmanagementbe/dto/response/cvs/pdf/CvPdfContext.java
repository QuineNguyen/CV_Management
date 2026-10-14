package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/*
 * Everything cv-template.xhtml prints, already resolved to display text in the CV's language.
 * Sections arrive in template order with the empty ones removed, so the template only loops.
 */
@Schema(name = "CvPdfContext", description = "Everything cv-template.xhtml prints, already resolved to display text in the CV's language. Sections arrive in template order with the empty ones removed, so the template only loops")
public record CvPdfContext(
        String lang,
        String documentTitle,
        PdfHeaderDto header,
        List<PdfSectionDto> sections
) {
}
