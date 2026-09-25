package com.training.emailservice.service.impl;

import com.training.emailservice.entity.EmailLog;
import com.training.emailservice.service.EmailFinalFailureHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingFinalFailureHook implements EmailFinalFailureHook {

    @Override
    public void onFinalFailure(EmailLog emailLog) {
        log.warn("Email {} ({}) to {} failed for good after {} retries: {}",
                emailLog.getId(), emailLog.getEventType(), emailLog.getRecipientEmail(),
                emailLog.getRetryCount(), emailLog.getErrorMessage());
    }
}
