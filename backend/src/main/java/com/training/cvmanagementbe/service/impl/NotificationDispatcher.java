package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.constant.EmailQueue;
import com.training.cvmanagementbe.entity.models.EmailLog;
import com.training.cvmanagementbe.entity.models.InAppNotification;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.notifications.EmailStatus;
import com.training.cvmanagementbe.record.events.EmailMessage;
import com.training.cvmanagementbe.record.events.NotificationCommand;
import com.training.cvmanagementbe.repository.EmailLogRepository;
import com.training.cvmanagementbe.repository.InAppNotificationRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;

/*
 * Stores a notification pair, then hands the email to the broker.
 * - Both rows commit in their own transaction before anything is published, so a message never
 * points at an email_logs row that does not exist.
 * - Each recipient is isolated: one failure never costs the others their notification.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final InAppNotificationRepository notificationRepository;
    private final EmailLogRepository emailLogRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate requiresNew;
    private final String subjectPrefix;

    public NotificationDispatcher(InAppNotificationRepository notificationRepository,
                                  EmailLogRepository emailLogRepository,
                                  UserRepository userRepository,
                                  RabbitTemplate rabbitTemplate,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${app.notification.subject-prefix:[CV Management]}") String subjectPrefix) {
        this.notificationRepository = notificationRepository;
        this.emailLogRepository = emailLogRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.subjectPrefix = subjectPrefix;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void dispatchAll(Collection<NotificationCommand> commands) {
        commands.forEach(this::dispatch);
    }

    public void dispatch(NotificationCommand command) {
        if (command.notifiesActor()) {
            return;
        }

        EmailMessage message;
        try {
            message = requiresNew.execute(status -> persist(command));
        } catch (RuntimeException ex) {
            log.error("Could not store {} notification for {}", command.type(), command.recipientId(), ex);
            return;
        }

        if (message != null) {
            publish(message);
        }
    }

    private EmailMessage persist(NotificationCommand command) {
        User recipient = userRepository.findById(command.recipientId()).orElse(null);
        if (recipient == null) {
            log.warn("Recipient {} of {} not found; notification skipped", command.recipientId(), command.type());
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        notificationRepository.save(InAppNotification.builder()
                .recipientId(recipient.getId())
                .type(command.type())
                .content(command.content())
                .link(command.link())
                .read(false)
                .createdAt(now)
                .build());

        String subject = truncate(subjectPrefix + " " + command.subject(), MAX_SUBJECT_LENGTH);

        EmailLog emailLog = emailLogRepository.save(EmailLog.builder()
                .eventType(command.type())
                .recipientId(recipient.getId())
                .recipientEmail(recipient.getEmail())
                .subject(subject)
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .createdAt(now)
                .build());

        return new EmailMessage(
                emailLog.getId(),
                command.type().name(),
                recipient.getEmail(),
                recipient.getFullName(),
                subject,
                command.type().getTemplate().getFileName(),
                command.link(),
                command.templateVars()
        );
    }

    // The in-app row already stands; a broker outage only costs the email.
    private void publish(EmailMessage message) {
        try {
            rabbitTemplate.convertAndSend(EmailQueue.EXCHANGE, EmailQueue.SEND_ROUTING_KEY, message);
        } catch (AmqpException ex) {
            log.error("Could not queue email {}", message.emailLogId(), ex);
            markUndelivered(message, ex);
        }
    }

    private void markUndelivered(EmailMessage message, AmqpException cause) {
        try {
            requiresNew.executeWithoutResult(status -> emailLogRepository.markUndelivered(
                    message.emailLogId(),
                    EmailStatus.FAILED,
                    truncate("Queue unavailable: " + cause.getMessage(), MAX_ERROR_LENGTH)
            ));
        } catch (RuntimeException ex) {
            log.error("Could not mark email {} as failed", message.emailLogId(), ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
