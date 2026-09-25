package com.training.emailservice.service;

import com.training.emailservice.dto.EmailMessage;

public interface EmailDeliveryService {

    // Sends one queued email and settles its log row; delivery failures are retried or recorded, never thrown.
    void deliver(EmailMessage message);

    // Parks the message on the retry queue without touching the database.
    void postpone(EmailMessage message);
}
