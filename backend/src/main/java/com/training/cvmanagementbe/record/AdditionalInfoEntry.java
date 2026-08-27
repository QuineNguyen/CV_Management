package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

// Section 9 - REPEATED. Free-form titled blocks: awards, hobbies, references.
public record AdditionalInfoEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("title") String title,
        @JsonProperty("content") String content
) implements RepeatedEntry {

    public AdditionalInfoEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
    }
}
