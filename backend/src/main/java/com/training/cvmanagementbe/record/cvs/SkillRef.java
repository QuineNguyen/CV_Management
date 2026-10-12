package com.training.cvmanagementbe.record.cvs;

import java.util.UUID;

// One entry of the skills section as the catalogue sees it: The reference and the typed name
public record SkillRef(UUID skillId, String name) {
}
