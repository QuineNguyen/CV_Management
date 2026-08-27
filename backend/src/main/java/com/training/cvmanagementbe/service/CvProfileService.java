package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.CvProfileRequest;
import com.training.cvmanagementbe.dto.response.CvProfileResponse;
import com.training.cvmanagementbe.dto.response.EmployeeTeamResponse;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CvProfileService {

    // Active profiles of one employee, primary first.
    PagedResponse<CvProfileResponse> listByEmployee(UUID employeeId, Pageable pageable);

    // Teams the employee belongs to, used to populate the linked-team picker.
    List<EmployeeTeamResponse> listAssignableTeams(UUID employeeId);

    CvProfileResponse getById(UUID id);

    CvProfileResponse create(UUID employeeId, CvProfileRequest request);

    CvProfileResponse update(UUID id, CvProfileRequest request);

    // Soft delete; also soft-deletes the profile's CVs.
    void delete(UUID id);

    // Promotes one profile and demotes the previous primary in the same transaction.
    CvProfileResponse setPrimary(UUID id);

    /*
     * Guarantees the employee owns at least one profile.
     *
     * - Idempotent: with a profile already present it returns the current primary and creates
     * nothing. Otherwise it creates the first profile from the employee's primary team.
     */
    CvProfileResponse ensureProfileExists(UUID employeeId);
}
