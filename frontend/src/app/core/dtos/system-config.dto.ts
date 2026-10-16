export interface SystemConfigResponse {
    reminderSendHour: number;
    reminderEnabled: boolean;
    escalationThresholdDays: number;
    approvalSlaDays: number;
    // Last change across all settings; null before anyone edited them
    updatedByName: string | null;
    updatedAt: string | null;
}

// The whole settings form in one body
export interface UpdateSystemConfigRequest {
    reminderSendHour: number;
    reminderEnabled: boolean;
    escalationThresholdDays: number;
    approvalSlaDays: number;
}