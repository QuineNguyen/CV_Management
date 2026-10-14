package com.training.cvmanagementbe.enums.pdf;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/*
 * Printed labels of the language proficiency codes. Constant names are the stored codes.
 */
@RequiredArgsConstructor
public enum PdfLanguageLevel {

    BASIC("Cơ bản", "Basic", "基礎"),
    CONVERSATIONAL("Giao tiếp", "Conversational", "日常会話"),
    PROFESSIONAL("Chuyên nghiệp", "Professional", "ビジネス"),
    FLUENT("Lưu loát", "Fluent", "流暢"),
    NATIVE("Bản ngữ", "Native", "ネイティブ");

    private final String vi;
    private final String en;
    private final String ja;

    public static Optional<PdfLanguageLevel> fromCode(String code) {
        return Arrays.stream(values())
                .filter(level -> level.name().equalsIgnoreCase(code))
                .findFirst();
    }

    public String text(PdfLocale locale) {
        return switch (locale) {
            case VI -> vi;
            case EN -> en;
            case JA -> ja;
        };
    }
}
