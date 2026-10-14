package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.users.Role;

import java.time.Instant;
import java.util.UUID;

/*
 * What a signed PDF link grants: One version of one CV, read as the person it was issued to,
 * until expiresAt. Carried inside the token itself, so nothing is stored on the server.
 */
public record PdfLinkClaims(
        UUID cvId,
        UUID versionId,
        UUID userId,
        Role role,
        Instant expiresAt
) {
}
