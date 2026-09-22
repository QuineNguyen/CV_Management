package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.users.CreateUserRequest;
import com.training.cvmanagementbe.dto.request.users.DeactivateUserRequest;
import com.training.cvmanagementbe.dto.request.users.UpdateUserRequest;
import com.training.cvmanagementbe.dto.response.users.CreatedUserResponse;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.users.TechLeadOption;
import com.training.cvmanagementbe.dto.response.users.UserResponse;
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
