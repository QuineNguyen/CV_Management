package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/*
 * Section 1 - SINGLE. Seeded from the user account, then an independent snapshot: editing the CV
 * never writes back to users and vice versa.
 *
 * - No avatar field here on purpose. The avatar is the foreign key column (avatar_image_id)
 * on cv_drafts / cv_versions, never a JSON key.
 */
public record PersonalInfo(

        @JsonProperty("full_name") String fullName,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("address") String address,
        @JsonProperty("position") String position
) {

    public static PersonalInfo empty() {
        return new PersonalInfo(null, null, null, null, null, null);
    }
}
