package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.enums.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.service.ImageStorageService;
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

/**
 * Acceptance endpoints for the project skeleton.
 *
 * <p>These prove three environment assumptions <b>through the real path</b> — browser, reverse
 * proxy, application, database and object store — rather than through an in-memory unit test.
 * Each of the three is something that looks fine in isolation and only fails once the pieces are
 * wired together, which is precisely what a unit test cannot see.
 *
 * <p><b>Delete this whole package when the first real screens land.</b> Update requests belong to
 * the request module and images to the CV image module; leaving these endpoints behind would give
 * the system two ways to write the same tables, one of them unowned.
 */
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

    /**
     * Writes a row and reads it straight back out of the database.
     *
     * <p>Two assumptions are checked at once, because one write exercises both:
     * <ul>
     *   <li><b>Character set.</b> Non-Latin text must survive the round trip byte for byte. A
     *       wrong server character set silently mangles it, and by the time anyone notices, the
     *       migrations have already run against real data.</li>
     *   <li><b>Time zone.</b> A deadline is the last second of the chosen day. The client sends a
     *       date; the server, not the client, decides what time that means — so the rule holds for
     *       every caller. If containers run in UTC, the stored value lands seven hours off and the
     *       date itself changes.</li>
     * </ul>
     *
     * <p>It writes to the real update-requests table on purpose, not a scratch table. That means
     * the real uniqueness constraint applies: calling this twice with the same employee, profile
     * and language returns 422 rather than inserting a duplicate. That second call is worth making
     * by hand — it demonstrates in one step that the database constraint is live and that the
     * error handler translates it into something a client can read.
     */
    @PostMapping("/round-trip")
    @Transactional
    @Operation(summary = "Store and re-read non-Latin text and an end-of-day deadline")
    public Map<String, Object> roundTrip(@RequestBody @Valid RoundTripRequest req) {

        UUID adminId = bootstrapAdminId();
        CurrentActor.set(adminId, Role.ADMIN);
        try {
            LocalDateTime deadline = LocalDateTime.of(req.deadlineDate(), LocalTime.of(23, 59, 59));
            String language = req.language() == null ? "JA" : req.language();
            LocalDateTime now = LocalDateTime.now();

            jdbc.update("""
                    INSERT INTO update_requests
                      (employee_id, profile_id, language, cv_id, reason, deadline, status,
                       notification_failed, created_by, created_at, updated_by, updated_at)
                    VALUES (?, NULL, ?, NULL, ?, ?, 'PENDING', FALSE, ?, ?, ?, ?)
                    """, adminId, language, req.reason(), deadline, adminId, now, adminId, now);

            UUID id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", UUID.class);

            // Read back from the database rather than echoing the object we just built in memory.
            // Echoing would pass even with a broken character set, which would make this endpoint
            // worse than useless: it would certify the very thing it is supposed to catch.
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
    /**
     * Uploads a file through the <b>internal</b> object-store endpoint, then returns a URL signed
     * with the <b>public</b> one.
     *
     * <p>The split matters because the signature covers the host header. A URL signed against the
     * internal container name cannot be repaired by rewriting the string in the browser — changing
     * the host invalidates the signature. So the only place this can be got right is here, at
     * signing time, and the only honest test is loading the image in a browser.
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image and return a URL the browser can load directly")
    public Map<String, Object> uploadImage(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new ApiException.BadRequestException("The uploaded file is empty");
        }
        String objectKey = "smoke/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        storage.upload(file, objectKey);

        jdbc.update("INSERT INTO image_files (object_key, uploaded_by, uploaded_at) VALUES (?, ?, ?)",
                objectKey, bootstrapAdminId(), LocalDateTime.now());

        String url = storage.presignedUrl(objectKey);
        return Map.of(
                "objectKey", objectKey,
                "presignedUrl", url,
                // If this is false, the URL was signed against an internal hostname and no
                // amount of client-side work will make the image load.
                "signedWithPublicEndpoint", !url.contains("minio:9000")
        );
    }

    private UUID bootstrapAdminId() {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id LIMIT 1",
                    UUID.class);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("No bootstrap administrator found; did the seed run?");
        }
    }
}
