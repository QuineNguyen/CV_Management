package com.training.cvmanagementbe.dto.request.configs;

import com.training.cvmanagementbe.constant.SystemConfigLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// The whole settings form in one body; wrappers so a missing field fails @NotNull
@Schema(name = "UpdateSystemConfigRequest", description = "Request body for updating system configuration settings")
public record UpdateSystemConfigRequest(
        @NotNull
        @Min(SystemConfigLimits.MIN_HOUR)
        @Max(SystemConfigLimits.MAX_HOUR)
        Integer reminderSendHour,

        @NotNull
        Boolean reminderEnabled,

        @NotNull
        @Min(SystemConfigLimits.MIN_DAYS)
        @Max(SystemConfigLimits.MAX_DAYS)
        Integer escalationThresholdDays,

        @NotNull
        @Min(SystemConfigLimits.MIN_DAYS)
        @Max(SystemConfigLimits.MAX_DAYS)
        Integer approvalSlaDays
) {
}
