package com.training.emailservice.service.impl;

import com.training.emailservice.constant.EmailQueue;
import com.training.emailservice.dto.EmailDeliveryFailedMessage;
import com.training.emailservice.dto.EmailMessage;
import com.training.emailservice.entity.EmailLog;
import com.training.emailservice.service.EmailFinalFailureHook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/*
 * Logs every final failure and, for an email tied to a business row, reports it back so the
 * backend can flag that row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportingFinalFailureHook implements EmailFinalFailureHook {

    // Default exchange: the routing key is the queue name
    private static final String DEFAULT_EXCHANGE = "";

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void onFinalFailure(EmailLog emailLog, EmailMessage message) {
        log.warn("Email {} ({}) to {} failed for good after {} retries: {}",
                emailLog.getId(), emailLog.getEventType(), emailLog.getRecipientEmail(),
                emailLog.getRetryCount(), emailLog.getErrorMessage());
        report(emailLog, message.correlationId());
    }

    // Untracked emails carry no correlation id and need no callback
    private void report(EmailLog emailLog, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(DEFAULT_EXCHANGE, EmailQueue.FAILED_QUEUE,
                    new EmailDeliveryFailedMessage(correlationId));
        } catch (AmqpException ex) {
            // email_logs is FAILED already; only the business flag is missed
            log.error("Could not report failed email {} for {}", emailLog.getId());
        }
    }
}
