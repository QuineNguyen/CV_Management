package com.training.emailservice.service;

import com.training.emailservice.dto.EmailMessage;
import com.training.emailservice.entity.EmailLog;

/*
 * Called once an email is FAILED for good: out of retries or failing in a way a retry cannot fix
 */
public interface EmailFinalFailureHook {

    void onFinalFailure(EmailLog emailLog, EmailMessage message);
}
