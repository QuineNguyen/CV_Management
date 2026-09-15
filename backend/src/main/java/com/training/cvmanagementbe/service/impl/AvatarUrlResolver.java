package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.ImageFile;
import com.training.cvmanagementbe.repository.ImageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/*
 * Turns stored image ids into presigned URLs for response DTOs.
 *
 * - A missing image or an unreachable MinIO must never fail the call that happened to include an
 * avatar, so every failure degrades to null and the client falls back to initials.
 * - resolveAll exists because list endpoints would otherwise issue one lookup per row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarUrlResolver {

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

    private String presignedOrNull(String objectKey) {
        try {
            return storage.presignedUrl(objectKey);
        } catch (Exception e) {
            // Signing is the best effort: the surrounding request is about something else.
            log.warn("Could not sign object key {}: {}", objectKey, e.getMessage());
            return null;
        }
    }
}
