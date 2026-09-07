package com.training.cvmanagementbe.dto.request;

import com.training.cvmanagementbe.record.CvContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/*
 * Write CV content. Where it lands depends on the owner's choice:
 * an Employee owner updates a draft, an Admin/HR owner publishes a version directly.
 */
public record CvEditRequest(
        @NotNull
        @Valid
        CvContent content,

        UUID avatarImageId
) {
}
