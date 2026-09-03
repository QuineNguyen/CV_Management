package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.ResponseCode;
import io.swagger.v3.oas.annotations.media.Schema;

/*
 * The single body shape for every response, success or failure
 *
 * - code is the machine-readable outcome the frontend branches on: code SUCCESS on 2xx,
 * an ErrorCode enum name otherwise. data carries the payload on success and an ErrorDetail on failure.
 *
 * - Deliberately not annotated with @JsonInclude(NON_NULL): a command that returns nothing
 * must still serialise ("data": null) so the client sees one shape every time.
 */
@Schema(name = "ApiResponse", description = "Uniform envelope for every response body")
public record ApiResponse<T>(
        @Schema(example = "SUCCESS") String code,
        @Schema(example = "Request completed successfully") String message,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResponseCode.SUCCESS.code(), ResponseCode.SUCCESS.message(), data);
    }

    // For commands whose only outcome is "it worked"
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(ResponseCode.SUCCESS.code(), ResponseCode.SUCCESS.message(), null);
    }

    public static ApiResponse<ErrorDetail> failure(String code, String message, ErrorDetail detail) {
        return new ApiResponse<>(code, message, detail);
    }
}
