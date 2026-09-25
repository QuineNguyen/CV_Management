package com.training.emailservice.consumer;

import com.training.emailservice.constant.EmailQueue;
import com.training.emailservice.dto.EmailMessage;
import com.training.emailservice.service.EmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailDeliveryService deliveryService;

    @RabbitListener(queues = EmailQueue.SEND_QUEUE)
    public void consume(EmailMessage message) {
        try {
            deliveryService.deliver(message);
        } catch (DataAccessException ex) {
            // Database unreachable: park the message instead of dropping it.
            log.error("Database unavailable for email {}; postponed", message.emailLogId(), ex);
            deliveryService.postpone(message);
        }
    }
}
