package com.training.cvmanagementbe.record.events;

import java.util.UUID;

/*
 * How one notification is sent.
 * - correlationId: Echoed back by the email-service when the email fails for good.
 * - emailOnly: Skip the in-app row (a resend; the employee already has it in-app).
 */
public record DispatchOptions(UUID correlationId, boolean emailOnly) {

    public static final DispatchOptions DEFAULT = new DispatchOptions(null, false);

    public static DispatchOptions tracked(UUID correlationId) {
        return new DispatchOptions(correlationId, false);
    }

    public static DispatchOptions trackedEmailOnly(UUID correlationId) {
        return new DispatchOptions(correlationId, true);
    }
}
