package com.training.cvmanagementbe.enums.pdf;

import com.training.cvmanagementbe.enums.cvs.Language;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*
 * Language a PDF is printed in - always the CV's own language, never the viewer's.
 * Ranges read as month and year on a CV; single dates (birth, certificates) are printed in full.
 */
@Getter
@RequiredArgsConstructor
public enum PdfLocale {

    VI("vi", "MM/yyyy", "dd/MM/yyyy"),
    EN("en", "MMM yyyy", "dd MMM yyyy"),
    JA("ja", "yyyy'年'M'月'", "yyyy'年'M'月'd'日'");

    private final String code;
    private final String monthYearPattern;
    private final String fullDatePattern;

    // Exhaustive: A new language fails to compile here instead of silently printing in English
    public static PdfLocale of(Language language) {
        return switch (language) {
            case VI -> PdfLocale.VI;
            case EN -> PdfLocale.EN;
            case JA -> PdfLocale.JA;
        };
    }

    public Locale javaLocale() {
        return Locale.forLanguageTag(code);
    }

    public DateTimeFormatter monthYear() {
        return DateTimeFormatter.ofPattern(monthYearPattern, javaLocale());
    }

    public DateTimeFormatter fullDate() {
        return DateTimeFormatter.ofPattern(fullDatePattern, javaLocale());
    }
}
