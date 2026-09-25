package com.training.emailservice.constant;

// RabbitMQ topology. The first three names must match the main backend.
public final class EmailQueue {

    public static final String EXCHANGE = "email.exchange";
    public static final String SEND_QUEUE = "email.queue";
    public static final String SEND_ROUTING_KEY = "email.send";

    // Delayed-retry holding queue, owned by this service only
    public static final String RETRY_QUEUE = "email.retry.queue";
    public static final String RETRY_ROUTING_KEY = "email.retry";

    private EmailQueue() {

    }
}
