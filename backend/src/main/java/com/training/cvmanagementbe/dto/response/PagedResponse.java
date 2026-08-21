package com.training.cvmanagementbe.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/*
 * Stable pagination envelope. Spring's PageImpl has no serialization contract
 * and warns on direct exposure, so every paginated endpoint returns this instead.
 */
@Schema(name = "PagedResponse", description = "Stable pagination envelope for paginated responses")
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PagedResponse<T> of(Page<T> page) {
        return of(page, page.getContent());
    }

    // Used when the page was loaded as entities and mapped to DTOs separately.
    public static <T> PagedResponse<T> of(Page<?> page, List<T> content) {
        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
