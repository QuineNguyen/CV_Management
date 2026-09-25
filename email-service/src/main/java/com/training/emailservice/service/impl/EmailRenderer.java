package com.training.emailservice.service.impl;

import com.training.emailservice.config.EmailProperties;
import com.training.emailservice.dto.EmailMessage;
import com.training.emailservice.enums.EmailTemplate;
import com.training.emailservice.enums.LayoutVar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.Locale;

// Thymeleaf HTML rendering. th:text escapes every value, so user-written reasons are safe.
@Component
@RequiredArgsConstructor
public class EmailRenderer {

    private final ITemplateEngine templateEngine;
    private final EmailProperties properties;

    public String render(EmailTemplate template, EmailMessage message) {
        Context context = new Context(Locale.ENGLISH);
        if (message.templateVars() != null) {
            context.setVariables(message.templateVars());
        }

        // Layout variables are set last so a profucer var cannot override them.
        context.setVariable(LayoutVar.RECIPIENT_NAME.getKey(), message.recipientName());
        context.setVariable(LayoutVar.SUBJECT.getKey(), message.subject());
        context.setVariable(LayoutVar.ACTION_URL.getKey(), actionUrl(message.link()));
        context.setVariable(LayoutVar.APP_NAME.getKey(), properties.appName());
        context.setVariable(LayoutVar.YEAR.getKey(), Year.now().getValue());

        return templateEngine.process(template.path(), context);
    }

    private String actionUrl(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String base = properties.frontendBaseUrl() == null
                ? ""
                : properties.frontendBaseUrl().replaceAll("/+$", "");
        return base + (link.startsWith("/") ? link : "/" + link);
    }
}
