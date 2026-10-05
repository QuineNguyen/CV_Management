package com.training.cvmanagementbe.common;

import com.training.cvmanagementbe.enums.notifications.EmailTemplateVar;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Email template variables keyed by EmailTemplateVar, so a variable is never a loose string.
 * Null values are kept on purpose: Templates hide a line when its value is absent.
 */
public final class TemplateVars {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public TemplateVars with(EmailTemplateVar key, Object value) {
        values.put(key.getKey(), value);
        return this;
    }

    public Map<String, Object> build() {
        return values;
    }
}
