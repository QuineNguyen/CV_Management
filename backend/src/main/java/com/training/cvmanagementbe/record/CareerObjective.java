package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonProperty;

// Section 2 - SINGLE. A single free-text block.
public record CareerObjective(

        @JsonProperty("content") String content
) {

    public static CareerObjective empty() {
        return new CareerObjective(null);
    }
}
