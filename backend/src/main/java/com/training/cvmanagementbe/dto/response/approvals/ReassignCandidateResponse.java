package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.users.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/*
 * One person who may take over this level.
 * openAssignmentCount is included so the admin picks by workload instead of guessing - the same
 * signal the automatic resolver balances on.
 */
@Schema(name = "ReassignCandidateResponse", description = "A person eligible to review this draft")
public record ReassignCandidateResponse(
        UUID userId,
        String fullName,
        String username,
        Role role,
        int openAssignmentCount
) {
}
