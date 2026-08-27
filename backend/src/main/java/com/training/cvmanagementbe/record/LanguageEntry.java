package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.training.cvmanagementbe.enums.LanguageProficiency;

// Section 8 - REPEATED. Languages the employee speaks, not the language the CV is written in.
public record LanguageEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("language_name") String languageName,
        @JsonProperty("proficiency") LanguageProficiency proficiency,
        @JsonProperty("certification") String certification
) implements RepeatedEntry {

    public LanguageEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
    }
}
