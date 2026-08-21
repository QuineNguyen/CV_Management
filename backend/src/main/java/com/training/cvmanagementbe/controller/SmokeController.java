package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.enums.AccountStatus;
import com.training.cvmanagementbe.enums.Language;
import com.training.cvmanagementbe.enums.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.service.impl.ImageStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** Acceptance and health check endpoints for project skeleton verification. */
@RestController
@RequestMapping("/smoke")
@Tag(name = "Skeleton acceptance (temporary)")
@SecurityRequirements
public class SmokeController {

    private final JdbcTemplate jdbc;
    private final ImageStorageService storage;

    public SmokeController(JdbcTemplate jdbc, ImageStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    // ============================================================ text and time
    public record RoundTripRequest(
            @NotBlank String reason,
            @NotNull LocalDate deadlineDate,
            String language
    ) {}

    /** Verifies character set encoding and deadline timezone handling by storing and re-reading a row. */
    @PostMapping("/round-trip")
    @Transactional
    @Operation(summary = "Store and re-read non-Latin text and an end-of-day deadline")
    public Map<String, Object> roundTrip(@RequestBody @Valid RoundTripRequest req) {

        UUID adminId = bootstrapAdminId();
        CurrentActor.set(adminId, Role.ADMIN);
        try {
            LocalDateTime deadline = LocalDateTime.of(req.deadlineDate(), LocalTime.of(23, 59, 59));
            String language = req.language() == null ? Language.JA.name() : req.language();
            LocalDateTime now = LocalDateTime.now();

            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO update_requests
                      (id, employee_id, profile_id, language, cv_id, reason, deadline, status,
                       notification_failed, created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, NULL, ?, NULL, ?, ?, 'PENDING', FALSE, ?, ?, ?, ?)
                    """, id, adminId, language, req.reason(), deadline, adminId, now, adminId, now);

            // Read directly from DB to verify persistence and encoding.
            Map<String, Object> stored = jdbc.queryForMap(
                    "SELECT reason, deadline FROM update_requests WHERE id = ?", id);

            String reasonReadBack = (String) stored.get("reason");
            LocalDateTime deadlineReadBack =
                    ((java.sql.Timestamp) stored.get("deadline")).toLocalDateTime();

            return Map.of(
                    "id", id,
                    "reasonSent", req.reason(),
                    "reasonReadBack", reasonReadBack,
                    "textMatches", req.reason().equals(reasonReadBack),
                    "deadlineReadBack", deadlineReadBack,
                    "deadlineInLocalTime",
                    deadlineReadBack.toLocalTime().equals(LocalTime.of(23, 59, 59))
                            && deadlineReadBack.toLocalDate().equals(req.deadlineDate()),
                    "jvmTimeZone", ZoneId.systemDefault().getId()
            );
        } finally {
            CurrentActor.clear();
        }
    }

    @DeleteMapping("/round-trip")
    @Operation(summary = "Remove the rows this panel created so it can be run again")
    public Map<String, Object> clear() {
        int removed = jdbc.update(
                "DELETE FROM update_requests WHERE cv_id IS NULL AND profile_id IS NULL");
        return Map.of("removed", removed);
    }

    // ================================================================== images
    /** Uploads an image to storage and generates a publicly accessible presigned URL. */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image and return a URL the browser can load directly")
    public Map<String, Object> uploadImage(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new ApiException.BadRequestException("The uploaded file is empty");
        }
        String objectKey = UUID.randomUUID() + "-" + file.getOriginalFilename();
        storage.upload(file, objectKey);

        jdbc.update("INSERT INTO image_files (id, object_key, uploaded_by, uploaded_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), objectKey, bootstrapAdminId(), LocalDateTime.now());

        String url = storage.presignedUrl(objectKey);
        return Map.of(
                "objectKey", objectKey,
                "presignedUrl", url,
                // Verify URL is signed with public endpoint rather than internal container host.
                "signedWithPublicEndpoint", !url.contains("minio:9000")
        );
    }

    private UUID bootstrapAdminId() {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM users WHERE role = ? AND status = ? ORDER BY id LIMIT 1",
                    UUID.class, Role.ADMIN.name(), AccountStatus.ACTIVE.name());
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("No bootstrap administrator found; did the seed run?");
        }
    }
}
