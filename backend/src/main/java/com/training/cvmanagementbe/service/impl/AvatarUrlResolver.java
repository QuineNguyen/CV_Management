package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.ImageFile;
import com.training.cvmanagementbe.enums.pdf.PdfImageType;
import com.training.cvmanagementbe.repository.ImageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/*
 * Turns stored image ids into something that shows the image.
 *
 * - For response DTOs: a presigned URL, signed against the public endpoint the browser can reach.
 * - For PDF export: a data: URI built from bytes read through the internal endpoint. A presigned
 * URL points at the browser's host, which the backend container cannot reach - the renderer gets
 * "Connection refused" and silently drops the photo.
 * - A missing image or an unreachable MinIO must never fail the call that happened to include an
 * avatar, so every failure degrades to null and the client falls back to initials.
 * - resolveAll exists because list endpoints would otherwise issue one lookup per row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarUrlResolver {

    private static final String DATA_URI = "data:%s;base64,%s";

    private final ImageFileRepository imageFileRepository;
    private final ImageStorageService storage;

    public String resolve(UUID imageId) {
        if (imageId == null) {
            return null;
        }
        return imageFileRepository.findById(imageId)
                .map(image -> presignedOrNull(image.getObjectKey()))
                .orElse(null);
    }

    // Bulk variant: one query for the whole page, values may be null per id.
    public Map<UUID, String> resolveAll(Collection<UUID> imageIds) {
        Map<UUID, String> urls = new HashMap<>();

        Set<UUID> ids = imageIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return urls;
        }

        for (ImageFile image : imageFileRepository.findAllById(ids)) {
            String url = presignedOrNull(image.getObjectKey());
            if (url != null) {
                urls.put(image.getId(), url);
            }
        }
        return urls;
    }

    // For the PDF renderer only: The image embedded in the document, no network access needed
    public String resolveDataUri(UUID imageId) {
        if (imageId == null) {
            return null;
        }
        return imageFileRepository.findById(imageId)
                .map(image -> dataUriOrNull(image.getObjectKey()))
                .orElse(null);
    }

    private String presignedOrNull(String objectKey) {
        try {
            return storage.presignedUrl(objectKey);
        } catch (Exception e) {
            // Signing is the best effort: the surrounding request is about something else.
            log.warn("Could not sign object key {}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    // A CV printed without its photo beats no CV at all, so nothing here fails the export
    private String dataUriOrNull(String objectKey) {
        try {
            byte[] bytes = storage.readBytes(objectKey);
            Optional<PdfImageType> type = PdfImageType.detect(bytes);
            if (type.isEmpty()) {
                // WebP, HEIC and the like cannot be embedded by the PDF engine
                log.warn("Object key {} left out of the PDF: format cannot be embedded", objectKey);
                return null;
            }
            return DATA_URI.formatted(type.get().getMimeType(), Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            log.warn("Could not read object key {}: {}", objectKey, e.getMessage());
            return null;
        }
    }
}
