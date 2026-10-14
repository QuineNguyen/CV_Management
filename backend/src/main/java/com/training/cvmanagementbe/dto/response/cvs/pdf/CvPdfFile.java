package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

// A rendered PDF and the name it is saved under
@Schema(name = "CvPdfFile", description = "A rendered PDF and the name it is saved under")
public record CvPdfFile(
        String fileName,
        byte[] content
) {
}
