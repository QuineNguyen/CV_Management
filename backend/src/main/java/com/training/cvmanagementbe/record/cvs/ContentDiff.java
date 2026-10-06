package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.dto.response.cvs.DiffStats;
import com.training.cvmanagementbe.dto.response.cvs.SectionDiff;

import java.util.List;

// Pure content comparison; CvServiceImpl adds the version headers around it.
public record ContentDiff(
        List<SectionDiff> sections,
        DiffStats stats
) {
}
