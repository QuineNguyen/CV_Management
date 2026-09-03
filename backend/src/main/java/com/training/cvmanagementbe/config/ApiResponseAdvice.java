package com.training.cvmanagementbe.config;

import com.training.cvmanagementbe.dto.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/*
 * Wraps every controller return value in ApiResponse so no endpoint has to remember to.
 *
 * - Scoped to the controller package on purpose: springdoc serves the OpenAPI document from its
 * own controllers and wrapping that document breaks both SwaggerUI and code generation.
 * GlobalExceptionHandler sits outside this package too, so its bodies are never re-wrapped.
 */
@RestControllerAdvice(basePackages = "com.training.cvmanagementbe.controller")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // A String body is written by StringHttpMessageConverter, which cannot serialise an
        // ApiResponse; wrapping there fails with ClassCastException at write time.
        return !StringHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // Already an envelope: an endpoint built it explicitly or it came from an error handler.
        if (body instanceof ApiResponse<?>) {
            return body;
        }

        // Binary payloads (PDF export, MinIO streams) must stay raw.
        if (body instanceof Resource || body instanceof byte[]) {
            return body;
        }
        return ApiResponse.success(body);
    }
}
