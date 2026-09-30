package com.training.cvmanagementbe.config;

import com.training.cvmanagementbe.constant.EmailQueue;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailFeedbackRabbitConfig {

    // Declared by both services, so whichever starts first creates it
    @Bean
    public Queue emailFailedQueue() {
        return QueueBuilder.durable(EmailQueue.FAILED_QUEUE).build();
    }
}
