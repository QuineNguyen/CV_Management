package com.training.cvmanagementbe.exception;

import com.training.cvmanagementbe.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, ErrorCode error) {
        this(status, error.code(), error.message());
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    /** 400 — the request itself is malformed. */
    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.code(), message);
        }
    }

    /** 401 — no valid credentials, or the token was issued before the account's revocation mark. */
    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException() {
            super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
        }
        public UnauthorizedException(String message) {
            super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.code(), message);
        }
    }

    /**
     * 403 — the caller is authenticated but the row is outside the data scope their role grants.
     * Data scoping happens in the query, never in the UI, so this is the last line of defence.
     */
    public static class ForbiddenException extends ApiException {
        public ForbiddenException() {
            super(HttpStatus.FORBIDDEN, ErrorCode.OUT_OF_SCOPE);
        }
        public ForbiddenException(ErrorCode error) {
            super(HttpStatus.FORBIDDEN, error);
        }
    }

    /** 404 — no such row, or the caller may not even learn whether it exists. */
    public static class NotFoundException extends ApiException {
        public NotFoundException(String entity, Object id) {
            super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.code(),
                    "No " + entity + " found with id " + id);
        }
    }

    /**
     * 409 — a compare-and-set update matched zero rows, meaning someone else acted first.
     * Every draft state transition is written as
     * {UPDATE ... WHERE id = ? AND status = ? AND assignee = ?} precisely so this is
     * detectable instead of silently overwriting a decision.
     */
    public static class ConflictException extends ApiException {
        public ConflictException() {
            super(HttpStatus.CONFLICT, ErrorCode.STALE_STATE);
        }
        public ConflictException(ErrorCode error) {
            super(HttpStatus.CONFLICT, error);
        }
    }

    /** 422 — the request is well formed but breaks a business rule. */
    public static class BusinessRuleException extends ApiException {
        public BusinessRuleException(ErrorCode error) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, error);
        }
        public BusinessRuleException(ErrorCode error, String detail) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, error.code(), detail);
        }
    }
}
