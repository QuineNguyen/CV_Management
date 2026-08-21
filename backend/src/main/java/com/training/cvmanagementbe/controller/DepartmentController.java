package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.DepartmentRequest;
import com.training.cvmanagementbe.dto.response.DepartmentResponse;
import com.training.cvmanagementbe.dto.request.MoveDepartmentRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.enums.DepartmentSortField;
import com.training.cvmanagementbe.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPath.DEPARTMENTS)
@PreAuthorize(AuthorityExpression.ADMIN)
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department tree management")
public class DepartmentController {

    private static final Sort TREE_SORT = Sort.by(
            Sort.Order.asc(DepartmentSortField.DISPLAY_ORDER.getProperty()),
            Sort.Order.asc(DepartmentSortField.NAME.getProperty())
    );

    private final DepartmentService departmentService;

    @GetMapping(ApiPath.TREE)
    @Operation(summary = "Get the whole department tree")
    public ResponseEntity<PagedResponse<DepartmentResponse>> getTree(
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), TREE_SORT);
        return ResponseEntity.ok(departmentService.getTree(pageable));
    }

    @GetMapping
    @Operation(summary = "Flat paginated lookup, used by the parent picker")
    public ResponseEntity<PagedResponse<DepartmentResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID excludeSubtreeOf,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "DISPLAY_ORDER") DepartmentSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        Sort sort = Sort.by(direction, sortBy.getProperty())
                .and(Sort.by(Sort.Order.asc(DepartmentSortField.NAME.getProperty())));
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);

        return ResponseEntity.ok(departmentService.search(keyword, excludeSubtreeOf, pageable));
    }

    @GetMapping(ApiPath.BY_ID)
    @Operation(summary = "Get one department")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(departmentService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a department")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse created = departmentService.create(request);
        return ResponseEntity
                .created(URI.create(ApiPath.DEPARTMENTS + "/" + created.id()))
                .body(created);
    }

    @PutMapping(ApiPath.BY_ID)
    @Operation(summary = "Update a department")
    public ResponseEntity<DepartmentResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.update(id, request));
    }

    @DeleteMapping(ApiPath.BY_ID)
    @Operation(summary = "Delete an empty department")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(ApiPath.MOVE)
    @Operation(summary = "Reposition a department relative to its neighbours")
    public ResponseEntity<Void> move(@PathVariable UUID id,
                                     @Valid @RequestBody MoveDepartmentRequest request) {
        departmentService.move(id, request);
        return ResponseEntity.noContent().build();
    }
}
