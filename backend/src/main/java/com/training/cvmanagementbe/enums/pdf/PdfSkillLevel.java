package com.training.cvmanagementbe.enums.pdf;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/*
 * Printed labels of the skill proficiency codes. Constant names are the stored codes.
 */
@RequiredArgsConstructor
public enum PdfSkillLevel {

    BASIC("Cơ bản", "Basic", "基礎"),
    ELEMENTARY("Sơ cấp", "Elementary", "初級"),
    INTERMEDIATE("Trung cấp", "Intermediate", "中級"),
    ADVANCED("Nâng cao", "Advanced", "上級"),
    EXPERT("Chuyên gia", "Expert", "エキスパート");

    private final String vi;
    private final String en;
    private final String ja;

    public static Optional<PdfSkillLevel> fromCode(String code) {
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
