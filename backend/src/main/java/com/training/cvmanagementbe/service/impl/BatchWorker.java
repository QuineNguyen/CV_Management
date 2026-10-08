package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import com.training.cvmanagementbe.enums.notifications.DispatchOutcome;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.repository.BatchRequestRepository;
import com.training.cvmanagementbe.repository.UpdateRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * Sends the notifications of a batch in the background, one child at a time.
 * - Started after the creation/resend transaction commits, so every child row is readable.
 * - Each child's progress is its own REQUIRES_NEW transaction: one bad row never undoes the others.
 * - A cancelled batch stops the loop; each child is re-read, so one cancelled meanwhile gets no email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchWorker {

    private final UpdateRequestRepository updateRequestRepository;
    private final BatchRequestRepository batchRequestRepository;
    private final UpdateRequestNotifier notifier;
    private final BatchProgressRecorder recorder;

    @Async
    public void process(UUID batchId) {
        List<UUID> childIds = updateRequestRepository.findByBatchRequestIdAndNotificationFailedFalse(batchId)
                .stream()
                .map(UpdateRequest::getId)
                .toList();
        for (UUID childId : childIds) {
            // The cancel already told every pending employee; nothing more to send
            if (isCancelled(batchId)) {
                return;
            }
            boolean failed = !send(childId, false);
            safely(batchId, () -> recorder.recordProcessed(batchId, childId, failed));
        }
        safely(batchId, () -> recorder.finish(batchId));
    }

    /*
     * Resend: The children flagged when the resend was accepted.
     * The flag is cleared (error_count - 1) before sending; a new final failure raises both again.
     */
    @Async
    public void processFailedOnly(UUID batchId, List<UUID> requestIds) {
        for (UUID requestId : requestIds) {
            if (isCancelled(batchId)) {
                return;
            }
            safely(batchId, () -> recorder.clearFailure(batchId, requestId));
            boolean failed = !send(requestId, true);
            safely(batchId, () -> recorder.recordProcessed(batchId, requestId, failed));
        }
        safely(batchId, () -> recorder.finish(batchId));
    }

    // A batch that no longer exists is treated as cancelled
    private boolean isCancelled(UUID batchId) {
        return batchRequestRepository.findStatusById(batchId)
                .map(status -> status == BatchRequestStatus.CANCELLED)
                .orElse(true);
    }

    // Read fresh: A child cancelled or completed after the loop started gets no "please update" email
    private boolean send(UUID requestId, boolean emailOnly) {
        Optional<UpdateRequest> child = updateRequestRepository.findById(requestId);
        if (child.isEmpty() || child.get().getStatus() != RequestStatus.PENDING) {
            return true;
        }
        try {
            DispatchOutcome outcome = emailOnly
                    ? notifier.resendRequestedEmail(child.get())
                    : notifier.notifyRequested(child.get());
            // SKIPPED (recipient is the creator) no longer happens: the preview excludes self
            return outcome != DispatchOutcome.FAILED;
        } catch (RuntimeException e) {
            log.warn("Batch child {} could not be notified", requestId, e);
            return false;
        }
    }

    // A failing write is logged and skipped; the loop always reaches its end
    private void safely(UUID batchId, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Progress write failed for batch {}", batchId, e);
        }
    }
}
