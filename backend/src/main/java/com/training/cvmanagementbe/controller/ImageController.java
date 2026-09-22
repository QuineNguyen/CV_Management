package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.dto.response.profile_updates.ImageUploadResponse;
import com.training.cvmanagementbe.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/*
 * Images are uploaded on their own, before whatever will reference them is saved. That is what
 * lets the crop dialog show a preview without touching the CV or the user record and why an
 * image nobody ends up pointing at is simply left behind rather than cleaned up.
 *
 * Both endpoints only require a session: any signed-in user may render any avatar the UI shows
 * them, so there is no per-image scope check.
 */
@RestController
@RequestMapping(ApiPath.IMAGES)
@RequiredArgsConstructor
@Tag(name = "Images", description = "Avatar image upload and presigned URLs")
public class ImageController {

    private final ImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an avatar image and return its ID with a presigned URL")
    public ResponseEntity<ImageUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(imageService.upload(file));
    }
}
