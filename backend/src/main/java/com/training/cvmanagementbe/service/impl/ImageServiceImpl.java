package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.response.ImageUploadResponse;
import com.training.cvmanagementbe.entity.models.ImageFile;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.ImageFileRepository;
import com.training.cvmanagementbe.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImageServiceImpl implements ImageService {

    // The client already crops to 512x512 JPEG; this is the guard against a raw camera file.
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private final ImageFileRepository imageFileRepository;
    private final ImageStorageService storage;
    private final AuditLogger auditLogger;

    @Override
    @Transactional
    public ImageUploadResponse upload(MultipartFile file) {
        StorageContentType contentType = validate(file);

        // A fresh UUID per upload: keys are never reused, so nothing is ever overwritten.
        String objectKey = StorageFolder.AVATARS.keyOf(UUID.randomUUID() + contentType.getFileExtension());
        storage.upload(file, objectKey);

        ImageFile image = new ImageFile();
        image.setObjectKey(objectKey);

        ImageFile saved = imageFileRepository.save(image);
        auditLogger.record(Action.UPLOAD_IMAGE, TargetType.IMAGE_FILE, saved.getId(), null, objectKey);

        return new ImageUploadResponse(saved.getId(), storage.presignedUrl(objectKey));
    }

    // ---------- Validation ----------

    private StorageContentType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException.BadRequestException(ErrorCode.IMAGE_EMPTY);
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ApiException.BadRequestException(ErrorCode.IMAGE_TOO_LARGE);
        }

        StorageContentType contentType = StorageContentType.ofAvatar(file.getContentType());
        if (contentType == null) {
            throw new ApiException.BadRequestException(ErrorCode.IMAGE_UNSUPPORTED_TYPE);
        }
        return contentType;
    }
}
