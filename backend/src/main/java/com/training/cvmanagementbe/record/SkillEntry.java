package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.training.cvmanagementbe.enums.ProficiencyLevel;

import java.util.UUID;

/*
 * Section 3 - REPEATED.
 *
 * - skillId points at skills.id but carries no database foreign key: published content is
 * immutable, so the catalogue must never be able to invalidate it. That is also why the skill
 * catalogue has no delete action. skillName is the value captured at authoring time.
 */
public record SkillEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("skill_id") UUID skillId,
        @JsonProperty("skill_name") String skillName,
        @JsonProperty("proficiency") ProficiencyLevel proficiency,
        @JsonProperty("note") String note
) implements RepeatedEntry {

    public SkillEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
    }
}
