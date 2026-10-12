package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// Dropdown entry; depth indents departments so the list reads as the tree
@Schema(name = "CvSearchFilterOption", description = "Dropdown entry; depth indents departments so the list reads as the tree")
public record CvSearchFilterOption(UUID id, String code, String name, int depth) {
}
