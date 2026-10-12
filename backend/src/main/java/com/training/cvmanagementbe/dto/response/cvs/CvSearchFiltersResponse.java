package com.training.cvmanagementbe.dto.response.cvs;

import java.util.List;

/*
 * Lookup data for the search filters, readable by every role. Not paged: The department
 * dropdown must hold the whole tree to render it and both lists are small catalogues.
 */
public record CvSearchFiltersResponse(
        List<CvSearchFilterOption> departments,
        List<CvSearchFilterOption> teams
) {
}
