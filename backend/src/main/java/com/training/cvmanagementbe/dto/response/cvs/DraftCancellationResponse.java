package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/*
 * Why the owner's last draft is gone. Only built when someone other than the owner ended it:
 * discarding one's own draft needs no explanation.
 * - reason: null when an admin cancelled a draft that was not under review yet.
 */
@Schema(name = "DraftCancellationResponse", description = "Who cancelled the owner's last draft, when and why")
public record DraftCancellationResponse(
        String reason,
        String cancelledByName,
        LocalDateTime cancelledAt
) {
}
