package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.CreateUserRequest;
import com.training.cvmanagementbe.dto.request.DeactivateUserRequest;
import com.training.cvmanagementbe.dto.request.UpdateUserRequest;
import com.training.cvmanagementbe.dto.response.CreatedUserResponse;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.TechLeadOption;
import com.training.cvmanagementbe.dto.response.UserResponse;
import com.training.cvmanagementbe.enums.AccountStatus;
import com.training.cvmanagementbe.enums.Role;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {

    PagedResponse<UserResponse> search(String keyword,
                                       Role role,
                                       AccountStatus status,
                                       UUID departmentId,
                                       Pageable pageable);

    UserResponse getById(UUID id);

    // Returns the account plus the one-time temporary password.
    CreatedUserResponse create(CreateUserRequest request);

    UserResponse update(UUID id, UpdateUserRequest request);

    // Hands over led teams, cancels open work and revokes issued tokens.
    void deactivate(UUID id, DeactivateUserRequest request);

    void activate(UUID id);

    // Options for tech lead dropdowns: role TECH_LEAD and status ACTIVE.
    List<TechLeadOption> getActiveTechLeads();
}
