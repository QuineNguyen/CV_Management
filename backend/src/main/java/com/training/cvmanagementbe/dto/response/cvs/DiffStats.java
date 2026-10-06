package com.training.cvmanagementbe.dto.response.cvs;

/*
 * Overview counts. One unit = one entry of a REPEATED section, or one field of a SINGLE section.
 */
public record DiffStats(
        int added,
        int modified,
        int removed
) {
}
