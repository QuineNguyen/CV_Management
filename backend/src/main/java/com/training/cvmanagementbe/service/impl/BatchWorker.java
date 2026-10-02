package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.notifications.DispatchOutcome;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.repository.UpdateRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/*
 * Sends the notifications of a batch in the background, one child at a time.
 * - Started after the creation/resend transaction commits, so every child row is readable.
 * - Each child's progress is its own REQUIRES_NEW transaction: one bad row never undoes the others.
 * - A final failure (after the email-service's 3 retries) comes back later through
 * EmailFailureListener and flags the child then.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchWorker {

    private final UpdateRequestRepository updateRequestRepository;
    private final UpdateRequestNotifier notifier;
    private final BatchProgressRecorder recorder;

    @Async
    public void process(UUID batchId) {
        List<UpdateRequest> children = updateRequestRepository.findByBatchRequestIdAndNotificationFailedFalse(batchId);
        for (UpdateRequest child : children) {
            boolean failed = !send(child, false);
            safely(batchId, () -> recorder.recordProcessed(batchId, child.getId(), failed));
        }
        safely(batchId, () -> recorder.finish(batchId));
    }

    /*
     * Resend: The children flagged when the resend was accepted.
     * The flag is cleared (error_count - 1) before sending; a new final failure raises both again,
     * so a delivered email ends one lower and a repeated failure ends unchanged.
     */
    @Async
    public void processFailedOnly(UUID batchId, List<UUID> requestIds) {
        for (UUID requestId : requestIds) {
            safely(batchId, () -> recorder.clearFailure(batchId, requestId));
            boolean failed = updateRequestRepository.findById(requestId)
                    .map(child -> !send(child, true))
                    .orElse(false);
            safely(batchId, () -> recorder.recordProcessed(batchId, requestId, failed));
        }
        safely(batchId, () -> recorder.finish(batchId));
    }

    // False only when the email never reached the broker; the child is flagged right away
    private boolean send(UpdateRequest child, boolean emailOnly) {
        // Cancelled or completed meanwhile: A "please update" email would be wrong
        if (child.getStatus() != RequestStatus.PENDING) {
            return true;
        }
        try {
            DispatchOutcome outcome = emailOnly
                    ? notifier.resendRequestedEmail(child)
                    : notifier.notifyRequested(child);
            // SKIPPED (recipient is the creator) no longer happens: the preview excludes self
            return outcome != DispatchOutcome.FAILED;
        } catch (RuntimeException e) {
            // Building the message failed (missing data): same as never queued
            log.warn("Batch child {} could not be queued for notification", child.getId(), e);
            return false;
        }
    }

    // A failing write is logged and skipped; the loop always reaches finish()
    private void safely(UUID batchId, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Progress write failed for batch {}", batchId, e);
        }
    }
}
