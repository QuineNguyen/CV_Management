package com.training.cvmanagementbe.constant;

// RabbitMQ topology shared with email-service; names must match on bot side.
public final class EmailQueue {

    public static final String EXCHANGE = "email.exchange";
    public static final String SEND_QUEUE = "email.queue";
    public static final String SEND_ROUTING_KEY = "email.send";

    // Final-failure feedback from the email-service, through the default exchange
    public static final String FAILED_QUEUE = "email.failed.queue";

    private EmailQueue() {

    }
}
