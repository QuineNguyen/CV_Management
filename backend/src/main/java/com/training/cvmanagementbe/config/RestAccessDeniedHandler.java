package com.training.cvmanagementbe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.dto.response.ApiResponse;
import com.training.cvmanagementbe.dto.response.ErrorDetail;
import com.training.cvmanagementbe.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/*
 * Writes the standard envelope when an authenticated caller is refused at the filter chain.
 *
 * - Covers path-level rules only. A refusal from @PreAuthorize raises
 * AuthorizationDeniedException at the method layer, which GlobalExceptionHandler already maps
 * so today this handler fires rarely. It exists so that adding one path rile later cannot
 * silently reintroduce an empty 403 cody outside the response contract.
 *
 * - Uses OUT_OF_SCOPE, the same code GlobalExceptionHandler returns for 403. so the frontend
 * renders one message for one situation regardless of which layer cought it.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<ErrorDetail> body = ApiResponse.failure(
                ErrorCode.OUT_OF_SCOPE.code(),
                ErrorCode.OUT_OF_SCOPE.message(),
                ErrorDetail.of(HttpStatus.FORBIDDEN, request.getRequestURI())
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
