package com.training.emailservice.config;

import com.training.emailservice.constant.EmailQueue;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeliveryFeedbackRabbitConfig {

    @Bean
    public Queue emailFailedQueue() {
        return QueueBuilder.durable(EmailQueue.FAILED_QUEUE).build();
    }
}
