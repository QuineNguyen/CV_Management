package com.training.cvmanagementbe.record.reminders;

// One line of the digest, sent as the digestRows template variable
public record SlaDigestRow(
        String ownerName,
        String cvLabel,
        String levelLabel,
        String reviewerName,
        String dueAt,
        String overdueText,
        String cvUrl
) {
}
