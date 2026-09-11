package com.training.cvmanagementbe.enums;

/*
 * Lifecycle of a self-service profile update request.
 *
 * - PENDING is the only state a user can cancel and the only one an admin can act on.
 * APPROVED and REJECTED are terminal.
 */
public enum ProfileUpdateStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
