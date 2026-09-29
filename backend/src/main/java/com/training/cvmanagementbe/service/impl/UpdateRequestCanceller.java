package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.record.events.CvUpdateRequestCancelledEvent;
import com.training.cvmanagementbe.repository.UpdateRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * Every way an update request leaves PENDING for CANCELLED.
 * - Manual: compare-and-set on one row; the caller turns false into a 409.
 * - Automatic: PENDING rows are read first, then cancelled by the
 * same scope, so a row that slipped in meanwhile still leaves PENDING.
 * - Drafts are never touched: Cancelling only stops the reminders.
 * - MANDATORY: Part of the caller's transaction, so a rollback notifies nobody.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class UpdateRequestCanceller {

    private final UpdateRequestRepository updateRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public boolean cancelOne(UpdateRequest request, UUID actorId, LocalDateTime at) {
        int changed = updateRequestRepository.transitionById(
                request.getId(), RequestStatus.PENDING, RequestStatus.CANCELLED, actorId, at
        );
        if (changed == 0) {
            return false;
        }
        publish(List.of(request), actorId);
        return true;
    }

    // CV deleted
    public void cancelPendingForCv(UUID cvId, UUID actorId, LocalDateTime at) {
        List<UpdateRequest> pending = updateRequestRepository
                .findByCvIdAndStatusOrderByCreatedAtAsc(cvId, RequestStatus.PENDING);
        updateRequestRepository.transitionByCvId(
                cvId, RequestStatus.PENDING, RequestStatus.CANCELLED, actorId, at
        );
        publish(pending, actorId);
    }

    // Profile deleted: Requests on the profile or on any CV inside it
    public void cancelPendingForProfile(UUID profileId, UUID actorId, LocalDateTime at) {
        List<UpdateRequest> pending = updateRequestRepository
                .findByProfileScopeAndStatus(profileId, RequestStatus.PENDING);
        updateRequestRepository.transitionByProfileScope(
                profileId, RequestStatus.PENDING, RequestStatus.CANCELLED, actorId, at
        );
        publish(pending, actorId);
    }

    // Employee deactivated: Requests sent to them; the ones they created stay
    public void cancelPendingForEmployee(UUID employeeId, UUID actorId, LocalDateTime at) {
        List<UpdateRequest> pending = updateRequestRepository
                .findByEmployeeIdAndStatus(employeeId, RequestStatus.PENDING);
        updateRequestRepository.transitionByEmployeeId(
                employeeId, RequestStatus.PENDING, RequestStatus.CANCELLED, actorId, at
        );
        publish(pending, actorId);
    }

    // Delivered after commit by NotificationListener
    private void publish(List<UpdateRequest> requests, UUID actorId) {
        requests.forEach(request -> eventPublisher.publishEvent(new CvUpdateRequestCancelledEvent(
                request.getId(),
                request.getEmployeeId(),
                request.getCvId(),
                request.getProfileId(),
                request.getLanguage(),
                request.getReason(),
                actorId
        )));
    }
}
