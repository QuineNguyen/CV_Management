package com.training.cvmanagementbe.enums.pdf;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * Family names the fonts are registered under. cv-template.xhtml names the same families in its
 * font-family rules; single words so the CSS needs no quoting.
 */
@Getter
@RequiredArgsConstructor
public enum PdfFontFamily {

    NOTO_SANS("NotoSans"),
    NOTO_SANS_JP("NotoSansJP");

    private final String cssName;
}
