package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.common.TemplateVars;
import com.training.cvmanagementbe.entity.models.CvProfile;
import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.configs.EscalationLevel;
import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import com.training.cvmanagementbe.enums.notifications.NotificationLink;
import com.training.cvmanagementbe.enums.notifications.ReminderMessage;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.record.events.NotificationCommand;
import com.training.cvmanagementbe.record.reminders.ReminderNotice;
import com.training.cvmanagementbe.record.reminders.ReminderStepResult;
import com.training.cvmanagementbe.repository.CvProfileRepository;
import com.training.cvmanagementbe.repository.UpdateRequestRepository;
import com.training.cvmanagementbe.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.training.cvmanagementbe.enums.notifications.EmailTemplateVar.*;
import static com.training.cvmanagementbe.scheduler.ReminderLookups.ids;

/*
 * Every PENDING request, overdue ones included.
 * The CV owner is the only recipient at every level: HR follows overdue work on the dashboard.
 */
@Component
@RequiredArgsConstructor
public class UpdateRequestReminderStep {

    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_CREATE = "create";
    // Same target wording as the cancellation notices
    private static final String NEW_CV_TARGET = "a new CV (%s)";
    private static final String OWN_CV_TARGET = "your CV %s (%s)";

    private final UpdateRequestRepository updateRequestRepository;
    private final CvProfileRepository cvProfileRepository;
    private final ReminderService reminderService;

    public ReminderStepResult run(LocalDate today, int thresholdDays) {
        Map<UpdateRequest, EscalationLevel> due = new LinkedHashMap<>();
        for (UpdateRequest request : updateRequestRepository.findByStatusOrderByDeadlineAsc(RequestStatus.PENDING)) {
            EscalationLevel.forDeadline(today, request.getDeadline().toLocalDate(), thresholdDays)
                    .ifPresent(level -> due.put(request, level));
        }

        ReminderTally tally = new ReminderTally(ReminderTargetType.UPDATE_REQUEST);
        if (due.isEmpty()) {
            return tally.result();
        }

        Map<UUID, String> profileNames = cvProfileRepository
                .findAllById(ids(due.keySet(), UpdateRequest::getProfileId)).stream()
                .collect(Collectors.toMap(CvProfile::getId, CvProfile::getName));

        due.forEach((request, level) -> {
            tally.attempt(request.getId(),
                    () -> reminderService.remind(toNotice(request, level, profileNames.get(request.getProfileId()), today)));
        });
        return tally.result();
    }

    private ReminderNotice toNotice(UpdateRequest request, EscalationLevel level,
                                    String profileName, LocalDate today) {
        // Days left for APPROACHING, days overdue for OVERDUE, 0 on the last day
        long dayCount = Math.abs(ChronoUnit.DAYS.between(today, request.getDeadline().toLocalDate()));
        String language = request.getLanguage().name();
        boolean cvExists = request.getCvId() != null;
        String action = cvExists ? ACTION_UPDATE : ACTION_CREATE;
        String target = profileName == null
                ? NEW_CV_TARGET.formatted(language)
                : OWN_CV_TARGET.formatted(profileName, language);
        String deadline = ReminderFormats.date(request.getDeadline());

        ReminderMessage message = ReminderMessage.forUpdateRequest(level);
        Object[] args = {action, target, deadline, dayCount};

        NotificationCommand command = new NotificationCommand(
                request.getEmployeeId(),
                // No actor: The job is not a person
                null,
                NotificationEventType.REMINDER_UPDATE_REQUEST,
                message.content(args),
                linkOf(request, language),
                message.subject(args),
                new TemplateVars()
                        .with(PROFILE_NAME, profileName)
                        .with(LANGUAGE, language)
                        .with(DEADLINE, deadline)
                        .with(REASON, request.getReason())
                        .with(CV_EXISTS, cvExists)
                        .with(ESCALATION_LEVEL, level.name())
                        .with(DAY_COUNT, dayCount)
                        .build()
        );

        return new ReminderNotice(
                ReminderTargetType.UPDATE_REQUEST,
                request.getId(),
                level,
                today,
                command
        );
    }

    // Linked CV opens directly; otherwise the create screen, preselected
    private String linkOf(UpdateRequest request, String language) {
        if (request.getCvId() != null) {
            return NotificationLink.CV_DETAIL.path(request.getCvId());
        }
        return request.getProfileId() == null
                ? NotificationLink.CV_CREATE_FOR_LANGUAGE.path(language)
                : NotificationLink.CV_CREATE_FOR_PROFILE.path(request.getProfileId(), language);
    }
}
