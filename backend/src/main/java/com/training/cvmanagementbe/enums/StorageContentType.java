package com.training.cvmanagementbe.enums;

import java.util.Arrays;

/*
 * Content types the object store understands.
 *
 * - Avatars are restricted to JPEG and PNG: the browser crops to a JPEG canvas before upload, so
 * anything else arriving here is either a mistake or an attempt to store a non-image under an image key.
 */
public enum StorageContentType {

    JPEG("image/jpeg", ".jpg"),
    PNG("image/png", ".png"),
    OCTET_STREAM("application/octet-stream", "");

    private final String value;
    private final String fileExtension;

    StorageContentType(String value, String fileExtension) {
        this.value = value;
        this.fileExtension = fileExtension;
    }

    public String getValue() {
        return value;
    }

    // Extension appended to the generated object key, so the bucket stays browsable.
    public String getFileExtension() {
        return fileExtension;
    }

    // Returns the declared type when recognised, otherwise the neutral fallback.
    public static String resolveOrFallback(String declared) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(declared))
                .findFirst()
                .orElse(OCTET_STREAM)
                .getValue();
    }

    // Resolves an avatar upload's content type or null when it is not an accepted image.
    public static StorageContentType ofAvatar(String declared) {
        if (declared == null) {
            return null;
        }
        String normalized = declared.trim();
        return Arrays.stream(values())
                .filter(StorageContentType::isAvatarType)
                .filter(type -> type.value.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    private boolean isAvatarType() {
        return this == JPEG || this == PNG;
    }
}
