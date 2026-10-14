package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.entity.models.Cv;
import com.training.cvmanagementbe.entity.models.CvProfile;
import com.training.cvmanagementbe.entity.models.CvVersion;
import com.training.cvmanagementbe.enums.pdf.PdfLocale;

// One published version, already checked against the caller's read scope and ready to render
public record PdfExportTarget(
        Cv cv,
        CvProfile profile,
        CvVersion version,
        PdfLocale locale,
        String fileName
) {
}
