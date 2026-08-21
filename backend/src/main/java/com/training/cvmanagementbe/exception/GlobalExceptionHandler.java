package com.training.cvmanagementbe.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.record.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Database index and constraint names mapped to client-facing codes.
     * These names are a contract between the migrations, this class, and the schema test that
     * asserts the indexes exist. Renaming an index means editing all three.
     */
    private static final Map<String, ErrorCode> CODE_BY_INDEX = Map.ofEntries(
            // The eight "at most one row matching a condition" constraints.
            Map.entry(DbConstraint.UK_CV_PROFILES_ACTIVE_PRIMARY.getIndexName(),   ErrorCode.PRIMARY_PROFILE_EXISTS),
            Map.entry(DbConstraint.UK_CV_PROFILES_ACTIVE_NAME.getIndexName(),      ErrorCode.PROFILE_NAME_TAKEN),
            Map.entry(DbConstraint.UK_CVS_ACTIVE_PROFILE_LANG.getIndexName(),      ErrorCode.CV_LANGUAGE_EXISTS),
            Map.entry(DbConstraint.UK_CVS_ACTIVE_MASTER.getIndexName(),            ErrorCode.MASTER_CV_EXISTS),
            Map.entry(DbConstraint.UK_CV_DRAFTS_OPEN.getIndexName(),               ErrorCode.OPEN_DRAFT_EXISTS),
            Map.entry(DbConstraint.UK_APPROVAL_ASSIGNMENTS_ASSIGNED.getIndexName(),ErrorCode.APPROVAL_ALREADY_ASSIGNED),
            Map.entry(DbConstraint.UK_UPDATE_REQUESTS_PENDING.getIndexName(),      ErrorCode.PENDING_REQUEST_EXISTS),
            Map.entry(DbConstraint.UK_REMINDER_LOGS_DAILY.getIndexName(),          ErrorCode.REMINDER_ALREADY_SENT),
            // Plain unique keys and the composite foreign key.
            Map.entry(DbConstraint.UK_USERS_EMAIL.getIndexName(),                  ErrorCode.DUPLICATE_EMAIL),
            Map.entry(DbConstraint.UK_USERS_USERNAME.getIndexName(),               ErrorCode.DUPLICATE_USERNAME),
            Map.entry(DbConstraint.UK_CV_VERSIONS_NUMBER.getIndexName(),           ErrorCode.VERSION_NUMBER_TAKEN),
            Map.entry(DbConstraint.UK_SKILLS_CODE.getIndexName(),                  ErrorCode.DUPLICATE_SKILL_CODE),
            Map.entry(DbConstraint.UK_SKILLS_NAME.getIndexName(),                  ErrorCode.DUPLICATE_SKILL_NAME),
            Map.entry(DbConstraint.FK_UR_CV_PROFILE.getIndexName(),                ErrorCode.CV_PROFILE_MISMATCH),
            Map.entry(DbConstraint.UK_DEPARTMENT_CODE.getIndexName(),              ErrorCode.DUPLICATE_DEPARTMENT_CODE),
            Map.entry(DbConstraint.UK_DEPARTMENT_NAME.getIndexName(),              ErrorCode.DUPLICATE_DEPARTMENT_NAME)
    );

    // --------- 400 ---------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest req) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(OffsetDateTime.now(), HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.name(),
                ErrorCode.VALIDATION_FAILED.code(), ErrorCode.VALIDATION_FAILED.message(),
                req.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            JsonProcessingException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest req) {
        log.debug("400 at {}: {}", req.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, req);
    }

    // Locked account: carry the wait time in the standard Retry-After header.
    @ExceptionHandler(ApiException.AccountLockedException.class)
    ResponseEntity<ApiError> handleAccountLocked(ApiException.AccountLockedException ex, HttpServletRequest req) {
        log.info("{} {} at {} - retry after {} seconds", ex.status().value(), ex.code(),
                req.getRequestURI(), ex.retryAfterSeconds());
        return ResponseEntity.status(ex.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(ApiError.of(ex.status().value(), ex.status().name(), ex.code(),
                        ex.getMessage(), req.getRequestURI()));
    }

    // --------- 403 ---------
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    ResponseEntity<ApiError> handleAccessDenied(Exception ex, HttpServletRequest req) {
        log.info("403 at {} - {}", req.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.FORBIDDEN, ErrorCode.OUT_OF_SCOPE, req);
    }

    // --------- 404 ---------
    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ApiError> handleUnknownPath(HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, req);
    }

    // --------- 409 ---------
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleLostUpdate(HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, ErrorCode.STALE_STATE, req);
    }

    // --------- 409 or 422 ---------
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(DataIntegrityViolationException ex,
                                                       HttpServletRequest req) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        log.warn("Database rejected a write at {}: {}", req.getRequestURI(), cause);

        ErrorCode code = CODE_BY_INDEX.entrySet().stream()
                .filter(e -> cause.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(ErrorCode.CONFLICT);

        // A recognised constraint means a specific business rule was broken, which is 422.
        // An unrecognised one only tells us the written clashed with existing data, which is 409.
        HttpStatus status = code == ErrorCode.CONFLICT
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return respond(status, code, req);
    }

    // --------- Explicit business failures ---------
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest req) {
        log.info("{} {} at {} - {}", ex.status().value(), ex.code(), req.getRequestURI(),
                ex.getMessage());
        return ResponseEntity.status(ex.status()).body(ApiError.of(
                ex.status().value(), ex.status().name(), ex.code(), ex.getMessage(),
                req.getRequestURI()));
    }

    // --------- 500 ---------
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error at {}", req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, req);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, ErrorCode code,
                                             HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(), status.name(), code.code(), code.message(), req.getRequestURI()));
    }
}
