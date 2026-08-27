package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

// Section 5 - REPEATED. A null endDate means the education is still current.
public record EducationEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("institution") String institution,
        @JsonProperty("degree") String degree,
        @JsonProperty("field") String field,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("description") String description
) implements RepeatedEntry {

    public EducationEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
    }
}
