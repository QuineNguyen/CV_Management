package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/*
 * Result of a create call.
 * - skipped is only ever non-empty for ALL: those slots already had a PENDING request.
 * A single language in that state is refused outright instead.
 */
@Schema(name = "CreateUpdateRequestResponse", description = "Result of a create call")
public record CreateUpdateRequestResponse(
        List<UpdateRequestResponse> created,
        List<SkippedLanguageResponse> skipped
) {
}
