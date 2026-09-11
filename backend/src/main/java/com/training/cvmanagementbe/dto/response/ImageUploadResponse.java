package com.training.cvmanagementbe.dto.response;

import java.util.UUID;

// The id is what gets persisted on a user or CV; the URL is only good until it expires.
public record ImageUploadResponse(UUID id, String presignedUrl) {
}
