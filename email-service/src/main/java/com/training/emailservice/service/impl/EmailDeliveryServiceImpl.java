package com.training.emailservice.service.impl;

import com.training.emailservice.config.EmailProperties;
import com.training.emailservice.constant.EmailQueue;
import com.training.emailservice.dto.EmailMessage;
import com.training.emailservice.entity.EmailLog;
import com.training.emailservice.enums.EmailStatus;
import com.training.emailservice.enums.EmailTemplate;
import com.training.emailservice.repository.EmailLogRepository;
import com.training.emailservice.service.EmailDeliveryService;
import com.training.emailservice.service.EmailFinalFailureHook;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/*
 * Delivery with delayed retries.
 * retry_count counts retries already scheduled, so max-retries = 3 means four attempts in total:
 * the first send plus three retries, each after retry-delay-ms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryServiceImpl implements EmailDeliveryService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final EmailLogRepository emailLogRepository;
    private final EmailRenderer renderer;
    private final JavaMailSender mailSender;
    private final RabbitTemplate rabbitTemplate;
    private final EmailFinalFailureHook finalFailureHook;
    private final EmailProperties properties;

    @Override
    public void deliver(EmailMessage message) {
        EmailLog emailLog = emailLogRepository.findById(message.emailLogId()).orElse(null);
        if (emailLog == null) {
            log.warn("Email log {} not found; message dropped", message.emailLogId());
            return;
        }

        // A redelivery of an email already settled must never send it twice.
        if (emailLog.getStatus() != EmailStatus.PENDING) {
            log.info("Email log {} already {}; skipped", emailLog.getId(), emailLog.getStatus());
            return;
        }

        Optional<EmailTemplate> template = EmailTemplate.fromFileName(message.templateName());
        if (template.isEmpty()) {
            fail(emailLog, "Unknown template: " + message.templateName());
            return;
        }

        String html;
        try {
            html = renderer.render(template.get(), message);
        } catch (TemplateEngineException ex) {
            // Rendering is deterministic: a retry would fail the same way.
            fail(emailLog, "Template error: " + ex.getMessage());
            return;
        }

        try {
            send(message, html);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            retryOrFail(emailLog, message, ex);
            return;
        }

        // error_message is kept: it tells why earlier attempts needed a retry.
        emailLog.setStatus(EmailStatus.SENT);
        emailLog.setSentAt(LocalDateTime.now());
        emailLogRepository.save(emailLog);
    }

    @Override
    public void postpone(EmailMessage message) {
        scheduleRetry(message);
    }

    private void send(EmailMessage message, String html) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from(), properties.fromName());
        helper.setTo(message.recipientEmail());
        helper.setSubject(message.subject());
        helper.setText(html, true);
        mailSender.send(mime);
    }

    private void retryOrFail(EmailLog emailLog, EmailMessage message, Exception cause) {
        String error = truncate(cause.getMessage());

        if (emailLog.getRetryCount() >= properties.maxRetries()) {
            fail(emailLog, error);
            return;
        }

        emailLog.setRetryCount(emailLog.getRetryCount() + 1);
        emailLog.setErrorMessage(error);
        emailLogRepository.save(emailLog);

        try {
            scheduleRetry(message);
            log.warn("Email {} failed, retry {}/{} in {} ms: {}", emailLog.getId(),
                    emailLog.getRetryCount(), properties.maxRetries(), properties.retryDelayMs(), error);
        } catch (AmqpException ex) {
            fail(emailLog, "Could not schedule retry: " + ex.getMessage());
        }
    }

    // Per-message TTL: The retry queue dead-letters it back to email.queue when it expires.
    private void scheduleRetry(EmailMessage message) {
        rabbitTemplate.convertAndSend(EmailQueue.EXCHANGE, EmailQueue.RETRY_ROUTING_KEY, message, outgoing -> {
            outgoing.getMessageProperties().setExpiration(String.valueOf(properties.retryDelayMs()));
            return outgoing;
        });
    }

    private void fail(EmailLog emailLog, String error) {
        emailLog.setStatus(EmailStatus.FAILED);
        emailLog.setErrorMessage(truncate(error));
        emailLogRepository.save(emailLog);
        finalFailureHook.onFinalFailure(emailLog);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
