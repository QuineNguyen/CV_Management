package com.training.cvmanagementbe.record.events;

import java.util.List;
import java.util.UUID;

/*
 * Teams handed to one replacement Tech Lead in a single deactivation.
 * One event per replacement, so several teams arrive as one notification.
 */
public record TeamHandoverEvent(
        UUID replacementTechLeadId,
        UUID previousTechLeadId,
        UUID actorId,
        List<UUID> teamIds
) {

    public TeamHandoverEvent {
        teamIds = List.copyOf(teamIds);
    }
}
