package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.Language;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/*
 * Everything the review screen needs in one round trip: the content to read, the assignment that
 * grants the right to read it and the history that explains what earlier rounds asked for.
 * - The draft content arrives already decoded, exactly as the edit screen receives it, so the
 * reviewer's read-only view and the employee's editor render from the same shape.
 */
@Schema(name = "DraftReviewResponse", description = "Everything the review screen needs in one round trip")
public record DraftReviewResponse(
        CvDraftResponse draft,
        Language cvLanguage,
        String profileName,
        String employeeName,
        String avatarUrl,
        ApprovalAssignmentResponse currentAssignment,
        List<ApprovalDecisionResponse> previousDecisions
) {
}
