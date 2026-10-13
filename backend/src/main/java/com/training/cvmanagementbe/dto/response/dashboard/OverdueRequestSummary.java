package com.training.cvmanagementbe.dto.response.dashboard;

import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.dashboard.UrgencyLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row of the deadline widget.
 * - requestId: Row key on the client
 * - cvId: Link to the CV; null while the request still waits for its CV
 * - profileName: Null when the request targets no profile yet
 */
@Schema(name = "OverdueRequestSummary", description = "One row of the deadline widget")
public record OverdueRequestSummary(
        UUID requestId,
        UUID cvId,
        String employeeName,
        String profileName,
        Language language,
        LocalDateTime deadline,
        long daysOverdue,
        UrgencyLevel urgency
) {
}
