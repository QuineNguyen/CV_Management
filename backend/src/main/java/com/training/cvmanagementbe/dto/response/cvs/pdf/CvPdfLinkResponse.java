package com.training.cvmanagementbe.dto.response.cvs.pdf;

import io.swagger.v3.oas.annotations.media.Schema;

/*
 * A short-lived link that opens one exported version in the browser.
 * - path is relative to the API base and ends with the file name, so a PDF viewer saves the file
 * under that name.
 * - expiresInSeconds rather than a timestamp: The client adds it to its own clock, so server and
 * browser time zones never have to agree.
 */
@Schema(name = "CvPdfLinkResponse", description = "A short-lived link that opens one exported version in the browser. - path is relative to the API base and ends with the file name, so a PDF viewer saves the file under that name. - expiresInSeconds rather than a timestamp: The client adds it to its own clock, so server and browser time zones never have to agree")
public record CvPdfLinkResponse(
        String path,
        String fileName,
        long expiresInSeconds
) {
}
