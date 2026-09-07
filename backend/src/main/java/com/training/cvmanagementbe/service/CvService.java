package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.CvCreateRequest;
import com.training.cvmanagementbe.dto.request.CvDeleteRequest;
import com.training.cvmanagementbe.dto.request.CvEditRequest;
import com.training.cvmanagementbe.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CvService {

    /*
     * Creates a CV in one profile and language, snapshotting personal_info from the user record
     * and auto-linking the oldest matching PENDING update request.
     */
    CvResponse create(UUID profileId, CvCreateRequest request);

    // Returns the current version - the row with the highest version_number.
    CvDetailResponse getById(UUID cvId);

    /*
     * Writes CV content. Only the owner may call it; the owner's role decides whether
     * the write lands in a draft or becomes a version straight away.
     */
    CvEditResponse edit(UUID cvId, CvEditRequest request);

    // Soft-deletes the CV. Blocked while a draft awaits approval; a master needs a successor.
    void delete(UUID cvId, CvDeleteRequest request);

    /*
     * Restores a soft-deleted CV once the three conditions hold:
     * 1. The (profile, language) slot is not already occupied by another ACTIVE CV (CV_SLOT_OCCUPIED).
     * 2. The parent profile is currently ACTIVE; if deleted, the profile must be restored first (CV_PROFILE_DELETED).
     * 3. Valid Master relationship: If the CV was previously a Master,
     *    its master_cv_id must be updated to point to the profile's current active Master within
     *    the same transaction (to avoid having two active masters, CV_MASTER_CONFLICT).
     *    If the profile currently has no other active CVs, keep master_cv_id null and it becomes Master.
     */
    CvResponse restore(UUID cvId);

    List<CvResponse> listByProfile(UUID profileId, boolean includeDeleted);

    PagedResponse<CvResponse> listDeleted(Pageable pageable);

    List<CvVersionSummary> listVersions(UUID cvId);
}
