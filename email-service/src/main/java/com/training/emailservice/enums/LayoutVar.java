package com.training.emailservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Variables the renderer adds for base-layout.html, on top of the producer's templateVars.
@Getter
@RequiredArgsConstructor
public enum LayoutVar {

    RECIPIENT_NAME("recipientName"),
    SUBJECT("subject"),
    ACTION_URL("actionUrl"),
    APP_NAME("appName"),
    YEAR("year");

    private final String key;
}
