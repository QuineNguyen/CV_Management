package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.constant.EmailQueue;
import com.training.cvmanagementbe.record.events.EmailDeliveryFailedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Callback for emails that exhausted their retries; flag + error_count in one transaction
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailFailureListener {

    private final BatchProgressRecorder recorder;

    @RabbitListener(queues = EmailQueue.FAILED_QUEUE)
    public void onEmailFailed(EmailDeliveryFailedMessage message) {
        UUID requestId;
        try {
            requestId = UUID.fromString(message.correlationId());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Unreadable reference: requeueing would only loop
            log.warn("Ignoring email failure with correlation id {}", message.correlationId());
            return;
        }
        recorder.recordEmailFailure(requestId);
    }
}
