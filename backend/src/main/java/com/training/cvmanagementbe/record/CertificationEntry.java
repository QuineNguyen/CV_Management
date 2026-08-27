package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

// Section 6 - REPEATED. A null expiryDate means the certificate does not expire.
public record CertificationEntry(

        @JsonProperty("item_id") String itemId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_untranslated") boolean untranslated,
        @JsonProperty("deleted_in_master") boolean deletedInMaster,

        @JsonProperty("name") String name,
        @JsonProperty("issuing_organization") String issuingOrganization,
        @JsonProperty("issue_date") LocalDate issueDate,
        @JsonProperty("expiry_date") LocalDate expiryDate,
        @JsonProperty("credential_id") String credentialId
) implements RepeatedEntry {

    public CertificationEntry {
        itemId = RepeatedEntry.ensureItemId(itemId);
    }
}
