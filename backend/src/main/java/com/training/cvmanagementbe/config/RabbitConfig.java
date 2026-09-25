package com.training.cvmanagementbe.config;

import com.training.cvmanagementbe.constant.EmailQueue;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Producer side. Declared with the same arguments as email-service: whichever starts first creates them.
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(EmailQueue.EXCHANGE, true, false);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EmailQueue.SEND_QUEUE).build();
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(emailExchange()).with(EmailQueue.SEND_ROUTING_KEY);
    }

    // Own converter, not the REST ObjectMapper: the queue contract must not follow API settings.
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
