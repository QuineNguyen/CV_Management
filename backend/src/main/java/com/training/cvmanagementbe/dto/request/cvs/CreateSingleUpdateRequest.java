package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.enums.cvs.UpdateRequestLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/*
 * - profileId null: "create a new profile and CV in this language".
 * - deadline is a date only; the server turns it into 23:59:59 of that day.
 * - anchoredNotes only for one language and an existing CV.
 */
@Schema(name = "CreateSingleUpdateRequest", description = "Request to create a single CV update request")
public record CreateSingleUpdateRequest(
        @NotNull
        UUID employeeId,

        UUID profileId,

        @NotNull
        UpdateRequestLanguage language,

        @NotBlank
        @Size(max = CreateSingleUpdateRequest.REASON_MAX_LENGTH)
        String reason,

        @NotNull
        LocalDate deadline,

        @Valid
        @Size(max = CreateSingleUpdateRequest.MAX_NOTES)
        List<AnchoredNoteRequest> anchoredNotes
) {
    public static final int REASON_MAX_LENGTH = 1000;
    public static final int MAX_NOTES = 50;
}
