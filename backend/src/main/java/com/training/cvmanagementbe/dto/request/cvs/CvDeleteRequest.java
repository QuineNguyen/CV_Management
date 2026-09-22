package com.training.cvmanagementbe.dto.request.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/*
 * newMasterCvId required only when deleting the master of a profile that still has other
 * ACTIVE CVs - the localisation chain must keep an anchor.
 */
@Schema(name = "CvDeleteRequest", description = "newMasterCvId required only when deleting the master of a profile that still has other ACTIVE CVs - the localisation chain must keep an anchor")
public record CvDeleteRequest(UUID newMasterCvId) {
}
