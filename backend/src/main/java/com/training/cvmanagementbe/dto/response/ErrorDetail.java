package com.training.cvmanagementbe.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;

import java.time.OffsetDateTime;
import java.util.List;

/*
 * The data payload of a failed response.
 *
 * - Everything here is diagnostic. The cause the fronted branched on lives in
 * ApiResponse.code(), so it is deliberately absent from this record - carrying it in
 * both places would create two sources of truth for the same fact.
 */
@Schema(name = "ErrorDetail", description = "Diagnostic payload of a failed response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(
        @Schema(example = "2026-08-07T14:30:00+07:00") OffsetDateTime timestamp,
        @Schema(example = "422") int status,
        @Schema(example = "UNPROCESSABLE_ENTITY") String error,
        @Schema(example = "/api/update-requests") String path,
        @Schema(description = "Per-field detail; present only on validation failures")
        List<FieldError> fieldErrors
        ) {
    @Schema(name = "ApiFieldError")
    public record FieldError(String field, String message) {}

    public static ErrorDetail of(HttpStatus status, String path) {
        return new ErrorDetail(OffsetDateTime.now(), status.value(), status.name(), path, null);
    }
}
