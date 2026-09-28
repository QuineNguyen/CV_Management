package com.training.cvmanagementbe.record.cvs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;

/*
 * One element of update_requests.anchored_notes.
 * - Same (section, item, field) coordinates as inline comments; item and field are optional.
 * - snake_case keys
 */
public record AnchoredNote(
        @JsonProperty("section_key") CvSectionKey sectionKey,
        @JsonProperty("item_id") String itemId,
        @JsonProperty("field_key") String fieldKey,
        @JsonProperty("note") String note
) {
}
