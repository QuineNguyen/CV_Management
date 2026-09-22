package com.training.cvmanagementbe.dto.response.approvals;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Result of one handover. Assignment ids are left out: the client addresses drafts, never
 * assignment rows. The new deadline is returned because the SLA restarts from now.
 */
@Schema(name = "ReassignResponse", description = "Result of transferring an approval assignment")
public record ReassignResponse(
        UUID draftId,
        UUID newAssigneeId,
        String newAssigneeName,
        LocalDateTime newDueAt
) {
}
