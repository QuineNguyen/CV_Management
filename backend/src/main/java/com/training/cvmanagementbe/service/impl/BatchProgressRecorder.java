package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import com.training.cvmanagementbe.repository.BatchRequestRepository;
import com.training.cvmanagementbe.repository.UpdateRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/*
 * Every write the worker and the failure callback make on a batch.
 * - REQUIRES_NEW: Each child commits on its own.
 * - Flag and error_count move in the same transaction, so error_count always equals the
 * number of flagged children.
 * - Bulk updates need no CurrentActor, which background threads do not have.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class BatchProgressRecorder {

    private final BatchRequestRepository batchRequestRepository;
    private final UpdateRequestRepository updateRequestRepository;

    public void recordProcessed(UUID batchId, UUID requestId, boolean failed) {
        int newlyFailed = failed ? updateRequestRepository.markNotificationFailed(requestId) : 0;
        batchRequestRepository.addProgress(batchId, newlyFailed);
    }

    public void clearFailure(UUID batchId, UUID requestId) {
        int cleared = updateRequestRepository.clearNotificationFailed(requestId);
        if (cleared > 0) {
            batchRequestRepository.addErrors(batchId, -cleared);
        }
    }

    // Email FAILED after its last retry; single requests only keep the flag
    public void recordEmailFailure(UUID requestId) {
        Optional<UpdateRequest> request = updateRequestRepository.findById(requestId);
        if (request.isEmpty()) {
            log.warn("Email failure for unknown update request {}", requestId);
            return;
        }
        // CAS on the flag: A redelivered message changes nothing
        int newlyFailed = updateRequestRepository.markNotificationFailed(requestId);
        UUID batchId = request.get().getBatchRequestId();
        if (newlyFailed == 0 || batchId == null) {
            return;
        }
        batchRequestRepository.addErrors(batchId, newlyFailed);
        // Late failure on a finished batch: error_count > 0 <=> COMPLETED_WITH_ERRORS
        batchRequestRepository.closeWithErrors(batchId,
                BatchRequestStatus.COMPLETED, BatchRequestStatus.COMPLETED_WITH_ERRORS);
    }

    public void finish(UUID batchId) {
        batchRequestRepository.closeClean(batchId,
                BatchRequestStatus.PROCESSING, BatchRequestStatus.COMPLETED);
        batchRequestRepository.closeWithErrors(batchId,
                BatchRequestStatus.PROCESSING, BatchRequestStatus.COMPLETED_WITH_ERRORS);
    }
}
