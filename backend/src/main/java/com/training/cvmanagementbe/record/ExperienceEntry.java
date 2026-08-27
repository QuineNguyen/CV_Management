package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/*
 * Section 4 - REPEATED. A null endDate means the position is still current.
 */
public record ExperienceEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("company") String company,
        @JsonProperty("position") String position,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("description") String description
) implements RepeatedEntry {

    public ExperienceEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
    }
}
