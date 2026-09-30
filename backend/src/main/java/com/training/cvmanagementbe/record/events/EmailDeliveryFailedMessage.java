package com.training.cvmanagementbe.record.events;

// Sent by the email-service when an email with a correlation id is FAILED after its last retry
public record EmailDeliveryFailedMessage(String correlationId) {
}
