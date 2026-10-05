package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.approvals.AssignmentStatus;
import com.training.cvmanagementbe.record.reminders.OverdueAssignment;
import com.training.cvmanagementbe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.training.cvmanagementbe.scheduler.ReminderLookups.ids;
import static com.training.cvmanagementbe.scheduler.ReminderLookups.indexById;

/*
 * ASSIGNED rows past due_at, loaded once per run with one query per table.
 * Both the personal reminders and the digest read this same list.
 */
@Component
@RequiredArgsConstructor
public class OverdueAssignmentFinder {

    private final ApprovalAssignmentRepository assignmentRepository;
    private final CvDraftRepository cvDraftRepository;
    private final CvRepository cvRepository;
    private final CvProfileRepository cvProfileRepository;
    private final UserRepository userRepository;

    // Oldest deadline first: The digest lists the longest overdue on top
    @Transactional(readOnly = true)
    public List<OverdueAssignment> find(LocalDateTime now) {
        List<ApprovalAssignment> assignments = assignmentRepository
                .findByStatusAndDueAtBeforeOrderByDueAtAsc(AssignmentStatus.ASSIGNED, now);
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<UUID, CvDraft> drafts = indexById(
                cvDraftRepository.findAllById(ids(assignments, ApprovalAssignment::getDraftId)), CvDraft::getId
        );
        Map<UUID, Cv> cvs = indexById(
                cvRepository.findAllById(ids(drafts.values(), CvDraft::getCvId)), Cv::getId
        );
        Map<UUID, CvProfile> profiles = indexById(
                cvProfileRepository.findAllById(ids(cvs.values(), Cv::getProfileId)), CvProfile::getId
        );

        Set<UUID> userIds = new HashSet<>(ids(assignments, ApprovalAssignment::getAssigneeId));
        userIds.addAll(ids(drafts.values(), CvDraft::getOwnerId));
        Map<UUID, String> names = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        LocalDate today = now.toLocalDate();
        List<OverdueAssignment> overdue = new ArrayList<>();
        for (ApprovalAssignment assignment : assignments) {
            if (assignment.getAssigneeId() == null) {
                continue;
            }
            CvDraft draft = drafts.get(assignment.getDraftId());
            Cv cv = draft == null ? null : cvs.get(draft.getCvId());
            CvProfile profile = cv == null ? null : profiles.get(cv.getProfileId());

            overdue.add(new OverdueAssignment(
                    assignment.getId(),
                    assignment.getDraftId(),
                    cv == null ? null : cv.getId(),
                    assignment.getAssigneeId(),
                    ReminderFormats.orUnknownPerson(names.get(assignment.getAssigneeId())),
                    ReminderFormats.orUnknownPerson(draft == null ? null : names.get(draft.getOwnerId())),
                    ReminderFormats.orUnknownValue(profile == null ? null : profile.getName()),
                    ReminderFormats.orUnknownValue(cv == null ? null : cv.getLanguage().name()),
                    assignment.approvalLevel(),
                    assignment.getAssignedAt(),
                    assignment.getDueAt(),
                    ChronoUnit.DAYS.between(assignment.getDueAt().toLocalDate(), today)
            ));
        }
        return overdue;
    }
}
