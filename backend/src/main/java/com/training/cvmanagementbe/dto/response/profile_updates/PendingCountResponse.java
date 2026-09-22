package com.training.cvmanagementbe.dto.response.profile_updates;

// Feeds the sidebar badge; kept typed rather than a bare Map so the client DTO can match.
public record PendingCountResponse(long count) {
}
