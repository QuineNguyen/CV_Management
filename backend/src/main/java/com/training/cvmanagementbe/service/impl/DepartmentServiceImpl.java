package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.DepartmentRequest;
import com.training.cvmanagementbe.dto.DepartmentResponse;
import com.training.cvmanagementbe.dto.ReorderRequest;
import com.training.cvmanagementbe.entity.models.Department;
import com.training.cvmanagementbe.enums.Action;
import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.enums.TargetType;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.DepartmentRepository;
import com.training.cvmanagementbe.repository.TeamRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    // Gap between siblings so a node can be inserted without renumbering
    private static final int ORDER_STEP = 10;

    // Safety net in case parent links are corrupted in the database
    private static final int MAX_TREE_DEPTH = 100;

    // Placeholder key for root nodes, whose parent id is null
    private static final UUID ROOT_KEY = new UUID(0L, 0L);

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AuditLogger auditLogger;

    @Override
    public List<DepartmentResponse> getTree() {
        List<Department> all = departmentRepository.findAllByOrderByDisplayOrderAscNameAsc();

        // One pass grouping, then an in-memory walk. Avoids N+1 on deep trees.
        Map<UUID, List<Department>> childrenByParent = all.stream()
                .collect(Collectors.groupingBy(
                        this::parentKeyOf,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return buildBranch(childrenByParent, ROOT_KEY, 0);
    }

    @Override
    public DepartmentResponse getById(UUID id) {
        return toFlatResponse(requireDepartment(id));
    }

    // ---------- Commands ----------

    @Override
    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        UUID parentId = request.parentDepartmentId();
        requireParentExists(parentId);

        Department department = new Department();
        applyRequest(department, request);
        department.setDisplayOrder(nextDisplayOrder(parentId));

        Department saved = departmentRepository.save(department);
        auditLogger.record(Action.CREATE_DEPARTMENT, TargetType.DEPARTMENT,
                saved.getId(), null, toFlatResponse(department));
        return toFlatResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse update(UUID id, DepartmentRequest request) {
        Department department = requireDepartment(id);
        UUID newParentId = request.parentDepartmentId();

        requireParentExists(newParentId);
        validateNoCircularReference(id, newParentId);

        boolean parentChanged = !Objects.equals(department.getParentDepartmentId(), newParentId);
        applyRequest(department, request);

        // Moving to another parent puts the node at the end of its new sibling list
        if (parentChanged) {
            department.setDisplayOrder(nextDisplayOrder(newParentId));
        }

        Department saved = departmentRepository.save(department);
        auditLogger.record(Action.UPDATE_DEPARTMENT, TargetType.DEPARTMENT,
                saved.getId(), toFlatResponse(department), toFlatResponse(saved));
        return toFlatResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Department department = requireDepartment(id);
        validateDeletable(id);

        departmentRepository.delete(department);
        auditLogger.record(Action.DELETE_DEPARTMENT, TargetType.DEPARTMENT,
                department.getId(), toFlatResponse(department), null);
    }

    @Override
    @Transactional
    public void reorder(UUID parentId, ReorderRequest request) {
        if (parentId != null) {
            requireDepartment(parentId);
        }

        List<Department> siblings = findChildren(parentId);
        Map<UUID, Department> byId = siblings.stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));

        validateReorderPayload(request.orderedIds(), byId.keySet());

        int order = ORDER_STEP;
        for (UUID id : request.orderedIds()) {
            byId.get(id).setDisplayOrder(order);
            order += ORDER_STEP;
        }

        departmentRepository.saveAll(siblings);
        auditLogger.record(Action.UPDATE_DEPARTMENT, TargetType.DEPARTMENT,
                parentId, null, new ReorderRequest(request.orderedIds()));
    }

    // ---------- Validation ----------

    // Walks up from newParentId; hitting movingId means the move closes a loop
    private void validateNoCircularReference(UUID movingId, UUID newParentId) {
        UUID cursor = newParentId;
        int depth = 0;

        while (cursor != null) {
            if (cursor.equals(movingId)) {
                throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
            }
            if (++depth > MAX_TREE_DEPTH) {
                throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
            }
            cursor = departmentRepository.findById(cursor)
                    .map(Department::getParentDepartmentId)
                    .orElse(null);
        }
    }

    private void validateDeletable(UUID id) {
        if (departmentRepository.existsByParentDepartmentId(id)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_HAS_CHILDREN);
        }
        if (userRepository.existsByPrimaryDepartmentId(id)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_HAS_EMPLOYEES);
        }
        if (teamRepository.existsByDepartmentId(id)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_HAS_TEAMS);
        }
    }

    // The payload must be a permutation of the current siblings, no more and no less
    private void validateReorderPayload(List<UUID> orderedIds, Set<UUID> siblingIds) {
        Set<UUID> distinct = new HashSet<>(orderedIds);
        if (distinct.size() != orderedIds.size() || !distinct.equals(siblingIds)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_INVALID_REORDER);
        }
    }

    // ---------- Private helpers ----------

    private List<DepartmentResponse> buildBranch(Map<UUID, List<Department>> childrenByParent,
                                                 UUID parentKey,
                                                 int depth) {
        if (depth > MAX_TREE_DEPTH) {
            return List.of();
        }
        List<Department> children = childrenByParent.getOrDefault(parentKey, List.of());
        List<DepartmentResponse> result = new ArrayList<>(children.size());

        for (Department child : children) {
            result.add(toResponse(child, buildBranch(childrenByParent, child.getId(), depth + 1)));
        }
        return result;
    }

    private List<Department> findChildren(UUID parentId) {
        return parentId == null
                ? departmentRepository.findByParentDepartmentIdIsNullOrderByDisplayOrderAscNameAsc()
                : departmentRepository.findByParentDepartmentIdOrderByDisplayOrderAscNameAsc(parentId);
    }

    private int nextDisplayOrder(UUID parentId) {
        int max = (parentId == null
                ? departmentRepository.findTopByParentDepartmentIdIsNullOrderByDisplayOrderDesc()
                : departmentRepository.findTopByParentDepartmentIdOrderByDisplayOrderDesc(parentId))
                .map(Department::getDisplayOrder)
                .orElse(0);
        return max + ORDER_STEP;
    }

    private void applyRequest(Department target, DepartmentRequest request) {
        target.setCode(request.code());
        target.setName(request.name());
        target.setParentDepartmentId(request.parentDepartmentId());
    }

    private UUID parentKeyOf(Department department) {
        return department.getParentDepartmentId() == null ? ROOT_KEY : department.getParentDepartmentId();
    }

    private Department requireDepartment(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("department", id));
    }

    private void requireParentExists(UUID parentId) {
        if (parentId != null && !departmentRepository.existsById(parentId)) {
            throw new ApiException.NotFoundException("department", parentId);
        }
    }

    private DepartmentResponse toFlatResponse(Department department) {
        return toResponse(department, List.of());
    }

    private DepartmentResponse toResponse(Department department, List<DepartmentResponse> children) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getParentDepartmentId(),
                department.getDisplayOrder(),
                children
        );
    }
}
