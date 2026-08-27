package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

// Section 7 - REPEATED. technologies is a free-text tag list, not a catalogue reference.
public record ProjectEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("name") String name,
        @JsonProperty("role") String role,
        @JsonProperty("team_size") Integer teamSize,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("description") String description,
        @JsonProperty("technologies") List<String> technologies
) implements RepeatedEntry {

    public ProjectEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
