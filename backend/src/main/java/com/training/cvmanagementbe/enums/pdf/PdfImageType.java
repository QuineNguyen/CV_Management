package com.training.cvmanagementbe.enums.pdf;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/*
 * Image formats the PDF engine can embed, recognised by their leading bytes.
 * Anything else (WebP, HEIC) is left out of the PDF rather than breaking the export.
 */
@RequiredArgsConstructor
public enum PdfImageType {

    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
    GIF("image/gif", new byte[]{0x47, 0x49, 0x46, 0x38});

    @Getter
    private final String mimeType;
    private final byte[] signature;

    public static Optional<PdfImageType> detect(byte[] bytes) {
        return Arrays.stream(values())
                .filter(type -> type.matches(bytes))
                .findFirst();
    }

    private boolean matches(byte[] bytes) {
        if (bytes == null || bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
