package com.training.cvmanagementbe.dto.request.approvals;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/*
 * Body of a manual handover. The reason is mandatory: it is what the outgoing and incoming
 * reviewers are told and the only explanation the assignment row will ever carry.
 */
@Schema(name = "ReassignRequest", description = "Admin handover of an open approval assignment")
public record ReassignRequest(

        @NotNull
        UUID newAssigneeId,

        @NotBlank
        @Size(max = 2000)
        String reason
) {
}
