package com.training.cvmanagementbe.common;

import com.training.cvmanagementbe.dto.response.cvs.pdf.CvPdfFile;
import org.springframework.http.*;

/*
 * The one way a rendered CV leaves the backend, shared by the JWT endpoint and the signed link.
 * - Raw bytes: ApiRes
 */
public final class PdfResponses {

    private PdfResponses() {}

    public static ResponseEntity<byte[]> of(CvPdfFile file, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}
