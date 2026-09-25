package com.training.emailservice.service;

import com.training.emailservice.entity.EmailLog;

/*
 * Called once an email is FAILED for good. The default only logs; a later phase declares a
 * @Primary bean to escalate (admin notification, chat alert...).
 */
public interface EmailFinalFailureHook {

    void onFinalFailure(EmailLog emailLog);
}
