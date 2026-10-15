package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.dto.request.audit.AuditLogFilterRequest;
import com.training.cvmanagementbe.dto.response.audit.AuditLogDetailResponse;
import com.training.cvmanagementbe.dto.response.audit.AuditLogResponse;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.entity.models.AuditLog;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.AuditLogRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.repository.specifications.AuditLogSpecification;
import com.training.cvmanagementbe.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Audit trail lookup (Admin only). Read-only: Entries are written by AuditLogger as part of
 * each business transaction, never through this service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    public PagedResponse<AuditLogResponse> search(AuditLogFilterRequest filter, Pageable pageable) {
        requireAdmin();

        Page<AuditLog> page = auditLogRepository.findAll(AuditLogSpecification.matching(filter), pageable);
        // Actors resolved for this page only, in one query
        Map<UUID, User> actors = loadActors(page.getContent().stream()
                .map(AuditLog::getActorId)
                .collect(Collectors.toSet()));

        List<AuditLogResponse> content = page.getContent().stream()
                .map(entry -> toResponse(entry, actors.get(entry.getActorId())))
                .toList();
        return PagedResponse.of(page, content);
    }

    @Override
    public AuditLogDetailResponse getById(UUID id) {
        requireAdmin();

        AuditLog entry = auditLogRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("audit log", id));
        User actor = userRepository.findById(entry.getActorId()).orElse(null);
        return toDetail(entry, actor);
    }

    @Override
    public List<Action> listActions() {
        requireAdmin();
        return Arrays.stream(Action.values())
                .sorted(Comparator.comparing(Action::name))
                .toList();
    }

    @Override
    public List<TargetType> listTargetTypes() {
        requireAdmin();
        return Arrays.stream(TargetType.values())
                .sorted(Comparator.comparing(TargetType::name))
                .toList();
    }

    // ---------- Helpers ----------

    private void requireAdmin() {
        if (CurrentActor.requireRole() != Role.ADMIN) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    private Map<UUID, User> loadActors(Set<UUID> actorIds) {
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    // actor is null only if the user row was removed, which the FK should prevent
    private static AuditLogResponse toResponse(AuditLog entry, User actor) {
        return new AuditLogResponse(
                entry.getId(),
                actor != null ? actor.getFullName() : null,
                actor != null ? actor.getEmail() : null,
                entry.getActorRole(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getCreatedAt()
        );
    }

    private static AuditLogDetailResponse toDetail(AuditLog entry, User actor) {
        return new AuditLogDetailResponse(
                entry.getId(),
                actor != null ? actor.getFullName() : null,
                actor != null ? actor.getEmail() : null,
                entry.getActorRole(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getOldValue(),
                entry.getNewValue(),
                entry.getCreatedAt()
        );
    }
}
