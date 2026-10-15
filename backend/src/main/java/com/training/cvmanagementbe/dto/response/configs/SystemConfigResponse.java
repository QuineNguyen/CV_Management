package com.training.cvmanagementbe.dto.response.configs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/*
 * Effective values, parsed the same way the scheduler and approval flow read them.
 * updatedByName / updatedAt describe the most recent change across all four keys.
 */
@Schema(name = "SystemConfigResponse", description = "Response body for system configuration settings")
public record SystemConfigResponse(
        int reminderSendHour,
        boolean reminderEnabled,
        int escalationThresholdDays,
        int approvalSlaDays,
        String updatedByName,
        LocalDateTime updatedAt
) {
}
