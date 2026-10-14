package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/*
 * Exactly one of text, ratings or entries carries content;
 * the template prints whichever is filled.
 */
@Schema(name = "PdfSectionDto", description = "Exactly one of text, ratings or entries carries content; the template prints whichever is filled")
public record PdfSectionDto(
        String title,
        String text,
        List<PdfRatingDto> ratings,
        List<PdfEntryDto> entries
) {

    public static PdfSectionDto ofText(String title, String text) {
        return new PdfSectionDto(title, text, List.of(), List.of());
    }

    public static PdfSectionDto ofRatings(String title, List<PdfRatingDto> ratings) {
        return new PdfSectionDto(title, null, ratings, List.of());
    }

    public static PdfSectionDto ofEntries(String title, List<PdfEntryDto> entries) {
        return new PdfSectionDto(title, null, List.of(), entries);
    }

    // A section with nothing to print is dropped, title included
    public boolean hasContent() {
        return text != null || !ratings.isEmpty() || !entries.isEmpty();
    }
}
