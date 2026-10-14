package com.training.cvmanagementbe.enums.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * TrueType files embedded into every PDF, read from the classpath (src/main/resources/fonts).
 * Must be .ttf with glyf outlines: PDFBox cannot embed CFF-based .otf, which is how the
 * noto-cjk repository ships Noto Sans JP - take the static TTFs from Google Fonts instead.
 */
@Getter
@RequiredArgsConstructor
public enum PdfFont {

    NOTO_SANS_REGULAR("fonts/NotoSans-Regular.ttf", PdfFontFamily.NOTO_SANS, 400, FontStyle.NORMAL),
    NOTO_SANS_BOLD("fonts/NotoSans-Bold.ttf", PdfFontFamily.NOTO_SANS, 700, FontStyle.NORMAL),
    NOTO_SANS_ITALIC("fonts/NotoSans-Italic.ttf", PdfFontFamily.NOTO_SANS, 400, FontStyle.ITALIC),
    NOTO_SANS_JP_REGULAR("fonts/NotoSansJP-Regular.ttf", PdfFontFamily.NOTO_SANS_JP, 400, FontStyle.NORMAL),
    NOTO_SANS_JP_BOLD("fonts/NotoSansJP-Bold.ttf", PdfFontFamily.NOTO_SANS_JP, 700, FontStyle.NORMAL);

    private final String classpath;
    private final PdfFontFamily family;
    private final int weight;
    private final FontStyle style;
}
