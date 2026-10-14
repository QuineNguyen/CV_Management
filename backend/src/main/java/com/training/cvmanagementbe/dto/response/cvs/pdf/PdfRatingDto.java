package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

// One row of the skills or languages table
@Schema(name = "PdfRatingDto", description = "One row of the skills or languages table")
public record PdfRatingDto(
        String name,
        String level,
        // Skill note or the certificate of a language
        String note
) {
}
