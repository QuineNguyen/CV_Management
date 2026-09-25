package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.record.cvs.CvContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/*
 * Write CV content. Where it lands depends on the owner's choice:
 * an Employee owner updates a draft, an Admin/HR owner publishes a version directly.
 */
@Schema(name = "CvEditRequest", description = "Write CV content. Where it lands depends on the owner's choice: an Employee owner updates a draft, an Admin/HR owner publishes a version directly.")
public record CvEditRequest(
        @NotNull
        @Valid
        CvContent content,

        UUID avatarImageId
) {
}
