package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.enums.notifications.DispatchOutcome;
import com.training.cvmanagementbe.record.reminders.ReminderNotice;

public interface ReminderService {

    /*
     * Logs the reminder, then dispatches email + in-app.
     * SKIPPED when the same reminder already went out on notice.sentDate().
     */
    DispatchOutcome remind(ReminderNotice notice);
}
