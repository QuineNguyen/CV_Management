package com.training.emailservice.dto;

// Reported back to the backend when an email with a correlation id is FAILED after its last retry
public record EmailDeliveryFailedMessage(String correlationId) {
}
