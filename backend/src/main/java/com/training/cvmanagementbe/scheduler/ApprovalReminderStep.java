package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.common.TemplateVars;
import com.training.cvmanagementbe.enums.configs.EscalationLevel;
import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import com.training.cvmanagementbe.enums.notifications.NotificationLink;
import com.training.cvmanagementbe.enums.notifications.ReminderMessage;
import com.training.cvmanagementbe.record.events.NotificationCommand;
import com.training.cvmanagementbe.record.reminders.OverdueAssignment;
import com.training.cvmanagementbe.record.reminders.ReminderNotice;
import com.training.cvmanagementbe.record.reminders.ReminderStepResult;
import com.training.cvmanagementbe.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static com.training.cvmanagementbe.enums.notifications.EmailTemplateVar.*;

// One reminder per overdue assignment, to the assignee only
@Component
@RequiredArgsConstructor
public class ApprovalReminderStep {

    private final ReminderService reminderService;

    public ReminderStepResult run(LocalDate today, List<OverdueAssignment> overdue) {
        ReminderTally tally = new ReminderTally(ReminderTargetType.APPROVAL_ASSIGNMENT);
        for (OverdueAssignment item : overdue) {
            tally.attempt(item.assignmentId(), () -> reminderService.remind(toNotice(item, today)));
        }
        return tally.result();
    }

    private ReminderNotice toNotice(OverdueAssignment item, LocalDate today) {
        String level = ReminderFormats.levelLabel(item.level());
        String overdueText = ReminderFormats.overdueText(item.overdueDays());
        Object[] args = {item.ownerName(), item.cvLabel(), level, overdueText};
        ReminderMessage message = ReminderMessage.APPROVAL_SLA_OVERDUE;

        NotificationCommand command = new NotificationCommand(
                item.assigneeId(),
                null,
                NotificationEventType.REMINDER_APPROVAL_ASSIGNMENT,
                message.content(args),
                NotificationLink.DRAFT_REVIEW.path(item.draftId()),
                message.subject(args),
                new TemplateVars()
                        .with(EMPLOYEE_NAME, item.ownerName())
                        .with(PROFILE_NAME, item.profileName())
                        .with(LANGUAGE, item.language())
                        .with(LEVEL_LABEL, level)
                        .with(ASSIGNED_AT, ReminderFormats.dateTime(item.assignedAt()))
                        .with(DUE_AT, ReminderFormats.dateTime(item.dueAt()))
                        .with(OVERDUE_TEXT, overdueText)
                        .build()
        );
        return new ReminderNotice(ReminderTargetType.APPROVAL_ASSIGNMENT, item.assignmentId(),
                EscalationLevel.SLA_OVERDUE, today, command);
    }
}
