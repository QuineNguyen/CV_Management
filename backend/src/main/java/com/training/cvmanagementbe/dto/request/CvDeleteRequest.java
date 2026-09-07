package com.training.cvmanagementbe.dto.request;

import java.util.UUID;

/*
 * newMasterCvId required only when deleting the master of a profile that still has other
 * ACTIVE CVs - the localisation chain must keep an anchor.
 */
public record CvDeleteRequest(UUID newMasterCvId) {
}
