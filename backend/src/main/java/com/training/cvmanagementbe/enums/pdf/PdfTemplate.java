package com.training.cvmanagementbe.enums.pdf;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// XHTML templates under src/main/resources/templates/pdf
@Getter
@RequiredArgsConstructor
public enum PdfTemplate {

    STANDARD_CV("cv-template");

    public static final String LOCATION = "templates/pdf/";
    public static final String EXTENSION = ".xhtml";

    private final String fileName;
}
