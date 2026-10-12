package com.training.cvmanagementbe.repository.projection;

import java.util.UUID;

// A department and its parent - enough to walk the tree
public record DepartmentLink(
        UUID id,
        UUID parentId,
        String code,
        String name
) {
}
