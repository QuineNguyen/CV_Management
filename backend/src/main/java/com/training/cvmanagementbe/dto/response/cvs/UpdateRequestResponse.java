package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * One row of the list. Ids are included only where the client uses them:
 * - employeeId: shows "Update CV" on rows addressed to the viewer.
 * - profileId / cvId: build the edit link, or the create link when no CV exists yet.
 */
@Schema(name = "UpdateRequestResponse", description = "One row of the list of CV update requests")
public record UpdateRequestResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        UUID profileId,
        String profileName,
        UUID cvId,
        Language language,
        String reason,
        LocalDateTime deadline,
        RequestStatus status,
        List<AnchoredNoteResponse> anchoredNotes,
        String createdByName,
        LocalDateTime createdAt,
        boolean cancellable
) {
}
