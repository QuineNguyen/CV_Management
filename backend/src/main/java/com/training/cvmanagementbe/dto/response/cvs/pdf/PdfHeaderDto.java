package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// Section 1 (personal info), printed as the page header
@Schema(name = "PdfHeaderDto", description = "Section 1 (personal info), printed as the page header")
public record PdfHeaderDto(
        String fullName,
        String position,
        // data: URI or null when the version has no photo, or it could not be read
        String avatarDataUri,
        List<PdfFieldDto> contacts
) {
}
