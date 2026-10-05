package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.common.FrontendUrlResolver;
import com.training.cvmanagementbe.common.TemplateVars;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.configs.EscalationLevel;
import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import com.training.cvmanagementbe.enums.notifications.NotificationLink;
import com.training.cvmanagementbe.enums.notifications.ReminderMessage;
import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.record.events.NotificationCommand;
import com.training.cvmanagementbe.record.reminders.*;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.training.cvmanagementbe.enums.notifications.EmailTemplateVar.DIGEST_ROWS;
import static com.training.cvmanagementbe.enums.notifications.EmailTemplateVar.OVERDUE_COUNT;

/*
 * One digest per ACTIVE Admin/HR per day, listing overdue approval assignments.
 * Same rows for everyone - an overdue HR sees their own line.
 */
@Component
@RequiredArgsConstructor
public class SlaDigestStep {

    // Admin first: The role that can reassign
    private static final List<Role> RECIPIENT_ROLES = List.of(Role.ADMIN, Role.HR);

    private final UserRepository userRepository;
    private final ReminderService reminderService;
    private final FrontendUrlResolver urlResolver;

    public ReminderStepResult run(LocalDate today, List<OverdueAssignment> overdue) {
        ReminderTally tally = new ReminderTally(ReminderTargetType.SLA_DIGEST);
        // Checked before loading recipients: An empty digest is never sent and costs no query
        if (overdue.isEmpty()) {
            return tally.result();
        }

        List<SlaDigestRow> rows = overdue.stream().map(this::toRow).toList();
        long oldestDays = overdue.stream().mapToLong(OverdueAssignment::overdueDays).max().orElse(0);
        Object[] args = {rows.size(), ReminderFormats.overdueText(oldestDays)};
        String subject = ReminderMessage.SLA_DIGEST.subject(args);
        String content = ReminderMessage.SLA_DIGEST.content(args);

        // Built once: Every recipient gets the same table
        Map<String, Object> vars = new TemplateVars()
                .with(OVERDUE_COUNT, rows.size())
                .with(DIGEST_ROWS, rows)
                .build();

        for (User recipient : recipients()) {
            tally.attempt(recipient.getId(),
                    () -> reminderService.remind(toNotice(recipient, subject, content, vars, today)));
        }
        return tally.result();
    }

    // ACTIVE only: A deactivated Admin/HR receives nothing
    private List<User> recipients() {
        return RECIPIENT_ROLES.stream()
                .flatMap(role -> userRepository
                        .findByRoleAndStatusOrderByFullNameAsc(role, AccountStatus.ACTIVE).stream())
                .toList();
    }

    private ReminderNotice toNotice(User recipient, String subject, String content,
                                    Map<String, Object> vars, LocalDate today) {
        // Admin lands on the oversight list to reassign; HR on their own queue
        String link = recipient.getRole() == Role.ADMIN
                ? NotificationLink.PENDING_DRAFTS.path()
                : NotificationLink.APPROVAL_QUEUE.path();

        NotificationCommand command = new NotificationCommand(
                recipient.getId(), null, NotificationEventType.SLA_DIGEST, content, link, subject, vars
        );

        // target_id = recipient_id: Deduplicated per recipient, not per assignment
        return new ReminderNotice(
                ReminderTargetType.SLA_DIGEST,
                recipient.getId(),
                EscalationLevel.SLA_OVERDUE,
                today,
                command
        );
    }

    // CV detail, not the review page: Only the assignee may open the draft itself
    private SlaDigestRow toRow(OverdueAssignment item) {
        return new SlaDigestRow(
                item.ownerName(),
                item.cvLabel(),
                ReminderFormats.levelLabel(item.level()),
                item.assigneeName(),
                ReminderFormats.dateTime(item.dueAt()),
                ReminderFormats.overdueText(item.overdueDays()),
                item.cvId() == null ? null : urlResolver.absolute(NotificationLink.CV_DETAIL.path(item.cvId()))
        );
    }
}
