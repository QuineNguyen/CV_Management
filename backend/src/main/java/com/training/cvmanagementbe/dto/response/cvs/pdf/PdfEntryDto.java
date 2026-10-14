package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/*
 * One entry of experience, education, certifications, projects or additional info.
 * The five sections share this shape, so the template has one block for all of them.
 */
@Schema(name = "PdfEntryDto", description = "One entry of experience, education, certifications, projects or additional info. The five sections share this shape, so the template has one block for all of them")
public record PdfEntryDto(
        String title,
        String subtitle,
        // Right-aligned beside the title: A date range or a single date
        String period,
        List<PdfFieldDto> details,
        String description
) {
}
