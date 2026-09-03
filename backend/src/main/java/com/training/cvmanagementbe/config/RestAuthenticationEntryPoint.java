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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Returns the standard ApiError body instead of Spring's default 403 page.
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<ErrorDetail> body = ApiResponse.failure(
                ErrorCode.UNAUTHENTICATED.code(),
                ErrorCode.UNAUTHENTICATED.message(),
                ErrorDetail.of(HttpStatus.UNAUTHORIZED, request.getRequestURI())
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
