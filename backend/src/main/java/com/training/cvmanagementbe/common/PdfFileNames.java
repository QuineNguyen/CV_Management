package com.training.cvmanagementbe.common;

import java.text.Normalizer;

/*
 * File name of an exported CV: CV_{Employee_Name}_{lang}_v{n}.pdf, e.g. CV_Nguyen_Van_A_vi_v2.pdf.
 * ASCII only - diacritics are folded - so the name survives every browser, mail client and OS.
 */
public final class PdfFileNames {

    private static final String PATTERN = "CV_%s_%s_v%d.pdf";
    private static final String FALLBACK_NAME = "Employee";

    private PdfFileNames() {}

    public static String of(String employeeName, String languageCode, int versionNumber) {
        return PATTERN.formatted(asciiSlug(employeeName), languageCode, versionNumber);
    }

    // "Nguyễn Văn Đức" -> "Nguyen_Van_Duc": đ has no decomposition, so it is mapped by hand
    private static String asciiSlug(String name) {
        if (name == null) {
            return FALLBACK_NAME;
        }
        String folded = Normalizer.normalize(name.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = folded.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? FALLBACK_NAME : slug;
    }
}
