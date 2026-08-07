package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One error shape for all six status codes (400/401/403/404/409/422).
 *
 * A single shape means the frontend needs exactly one branch for failures,
 * look up the message, show it. Per-endpoint error shapes are what push error handling into every
 * screen, which is the outcome this contract exists to prevent.
 */
@Schema(name = "ApiError", description = "Uniform error response body")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        @Schema(example = "2026-08-07T14:30:00+07:00") OffsetDateTime timestamp,
        @Schema(example = "422") int status,
        @Schema(example = "UNPROCESSABLE_ENTITY") String error,
        @Schema(description = "Stable, machine-readable cause", example = "PENDING_REQUEST_EXISTS")
        String code,
        @Schema(example = "A pending update request already exists") String message,
        @Schema(example = "/api/update-requests") String path,
        @Schema(description = "Per-field detail; present only on validation failures")
        List<FieldError> fieldErrors
) {
    @Schema(name = "ApiFieldError")
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, code, message, path, null);
    }
}
