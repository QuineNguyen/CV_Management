package com.training.emailservice.config;

import com.training.emailservice.constant.EmailQueue;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(EmailQueue.EXCHANGE, true, false);
    }

    // Same arguments as the main backend declares; a mismatch fails with PRECONDITION_FAILED.
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EmailQueue.SEND_QUEUE).build();
    }

    /*
     * Nobody consumes this queue. Each message carries its own TTL and once expired, is
     * dead-lettered back to email.queue: a delay that holds no consumer thread and survives restarts.
     */
    @Bean
    public Queue emailRetryQueue() {
        return QueueBuilder.durable(EmailQueue.RETRY_QUEUE)
                .deadLetterExchange(EmailQueue.EXCHANGE)
                .deadLetterRoutingKey(EmailQueue.SEND_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(emailExchange()).with(EmailQueue.SEND_ROUTING_KEY);
    }

    @Bean
    public Binding emailRetryBinding() {
        return BindingBuilder.bind(emailRetryQueue()).to(emailExchange()).with(EmailQueue.RETRY_ROUTING_KEY);
    }

    // The producer's __TypeId__ names a class that only exists in the main backend: use the listener type.
    @Bean
    public MessageConverter rabbitMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
