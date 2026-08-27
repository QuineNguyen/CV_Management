package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.DepartmentRequest;
import com.training.cvmanagementbe.dto.response.DepartmentResponse;
import com.training.cvmanagementbe.dto.request.MoveDepartmentRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.entity.models.Department;
import com.training.cvmanagementbe.enums.Action;
import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.enums.TargetType;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.DepartmentRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    private final AuditLogger auditLogger;

    @Override
    public PagedResponse<DepartmentResponse> getTree(Pageable pageable) {
        Page<Department> roots = departmentRepository.findByParentDepartmentIdIsNull(pageable);
        if (roots.isEmpty()) {
            return PagedResponse.of(roots, List.of());
        }

        // Second query loads the rest of the tree once; subtrees are attached in memory.
        Map<UUID, List<Department>> childrenByParent = groupByParent(
                departmentRepository.findAllByOrderByDisplayOrderAscNameAsc());

        List<DepartmentResponse> content = roots.getContent().stream()
                .map(root -> toResponse(root, buildBranch(childrenByParent, root.getId(), 1)))
                .toList();

        return PagedResponse.of(roots, content);
    }

    @Override
    public PagedResponse<DepartmentResponse> search(String keyword, UUID excludeSubtreeOf, Pageable pageable) {
        String pattern = (keyword == null || keyword.isBlank())
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        Set<UUID> excluded = excludeSubtreeOf == null
                ? Set.of(ROOT_KEY)
                : collectSubtreeIds(excludeSubtreeOf);

        Page<Department> page = departmentRepository.search(pattern, excluded, pageable);
        return PagedResponse.of(page, page.getContent().stream().map(this::toFlatResponse).toList());
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
    public void move(UUID id, MoveDepartmentRequest request) {
        Department moving = requireDepartment(id);
        UUID targetParentId = request.parentDepartmentId();

        requireParentExists(targetParentId);
        validateNoCircularReference(id, targetParentId);

        // Sibling list without the moving node, so anchor indexes are unambiguous.
        List<Department> siblings = new ArrayList<>(findChildren(targetParentId));
        siblings.removeIf(sibling -> sibling.getId().equals(id));

        int insertIndex = resolveInsertIndex(siblings, request);
        moving.setParentDepartmentId(targetParentId);
        siblings.add(insertIndex, moving);

        // Renumbering the whole group keeps the step clean; sibling groups stay small.
        int order = ORDER_STEP;
        for (Department sibling : siblings) {
            sibling.setDisplayOrder(order);
            order += ORDER_STEP;
        }

        departmentRepository.saveAll(siblings);
        auditLogger.record(Action.UPDATE_DEPARTMENT, TargetType.DEPARTMENT,
                id, null, toFlatResponse(moving));
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
    }

    // afterDepartmentId wins; beforeDepartmentId covers a drop at the top of a page.
    private int resolveInsertIndex(List<Department> siblings, MoveDepartmentRequest request) {
        if (request.afterDepartmentId() != null) {
            return indexOfAnchor(siblings, request.afterDepartmentId()) + 1;
        }
        if (request.beforeDepartmentId() != null) {
            return indexOfAnchor(siblings, request.beforeDepartmentId());
        }
        return 0;
    }

    private int indexOfAnchor(List<Department> siblings, UUID anchorId) {
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(anchorId)) {
                return i;
            }
        }
        throw new ApiException.BusinessRuleException(ErrorCode.DEPARTMENT_INVALID_MOVE_TARGET);
    }


    // ---------- Private helpers ----------

    private Map<UUID, List<Department>> groupByParent(List<Department> all) {
        return all.stream().collect(Collectors.groupingBy(
                this::parentKeyOf, LinkedHashMap::new, Collectors.toList()
        ));
    }

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

    // The node itself plus every descendant, used to keep the parent picker loop free
    private Set<UUID> collectSubtreeIds(UUID rootId) {
        Map<UUID, List<Department>> childrenByParent = groupByParent(
                departmentRepository.findAllByOrderByDisplayOrderAscNameAsc());

        Set<UUID> collected = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(rootId);

        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (!collected.add(current)) {
                continue;
            }
            for (Department child : childrenByParent.getOrDefault(current, List.of())) {
                pending.push(child.getId());
            }
        }

        return collected;
    }

    private List<Department> findChildren(UUID parentId) {
        return parentId == null
                ? departmentRepository.findByParentDepartmentIdIsNullOrderByDisplayOrderAscNameAsc()
                : departmentRepository.findByParentDepartmentIdOrderByDisplayOrderAscNameAsc(parentId);
    }

    private int nextDisplayOrder(UUID parentId) {
        List<Department> siblings = findChildren(parentId);
        return siblings.isEmpty()
                ? ORDER_STEP
                : siblings.get(siblings.size() - 1).getDisplayOrder() + ORDER_STEP;
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
