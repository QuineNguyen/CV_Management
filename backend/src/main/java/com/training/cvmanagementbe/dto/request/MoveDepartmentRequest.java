package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/*
 * Moves one department to a new position.
 *
 * Position is expressed by the two visible neighbours rather than an index, so the
 * client never needs to hold the whole sibling list. afterDepartmentId wins when both
 * anchors are present; both null means the first position under the parent.
 */
@Schema(name = "MoveDepartmentRequest", description = "Full sibling list of one parent, in the desired display order")
public record MoveDepartmentRequest(

        // Null moves the department to root level
        UUID parentDepartmentId,

        // Sibling the node is dropped below. Null when dropped at the top of a page.
        UUID afterDepartmentId,

        // Subling the node is dropped above. Only read when afterDepartmentId is null
        UUID beforeDepartmentId
) {
}
