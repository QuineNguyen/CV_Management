package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

// One "label: value" line; only built when the value is present
@Schema(name = "PdfFieldDto", description = "One 'label: value' line; only built when the value is present")
public record PdfFieldDto(
        String label,
        String value
) {
}
