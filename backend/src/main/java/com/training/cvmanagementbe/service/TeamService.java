package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.TeamRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.TeamMemberResponse;
import com.training.cvmanagementbe.dto.response.TeamResponse;
import com.training.cvmanagementbe.enums.TeamSortField;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

// Team management contract. ALl listing endpoints return paginated data.
public interface TeamService {

    // Paginated search by keyword (code, name) and optional department filter.
    PagedResponse<TeamResponse> search(String keyword,
                                       UUID departmentId,
                                       Pageable pageable);

    TeamResponse getById(UUID id);

    TeamResponse create(TeamRequest request);

    TeamResponse update(UUID id, TeamRequest request);

    // Only empty teams can be deleted: no member, no active CV profile linked.
    void delete(UUID id);

    List<TeamMemberResponse> getMembers(UUID teamId);

    // Adds an active user to the team. First membership of a user becomes primary.
    void addMember(UUID teamId, UUID userId);

    // Removes a membership unless it is the user's last or primary team.
    void removeMember(UUID teamId, UUID userId);
}
