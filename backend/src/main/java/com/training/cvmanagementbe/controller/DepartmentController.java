package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.dto.DepartmentRequest;
import com.training.cvmanagementbe.dto.DepartmentResponse;
import com.training.cvmanagementbe.dto.ReorderRequest;
import com.training.cvmanagementbe.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final DepartmentService departmentService;

    @GetMapping(ApiPath.TREE)
    @Operation(summary = "Get the whole department tree")
    public ResponseEntity<List<DepartmentResponse>> getTree() {
        return ResponseEntity.ok(departmentService.getTree());
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

    @PutMapping(ApiPath.REORDER_ROOTS)
    @Operation(summary = "Reorder root level departments")
    public ResponseEntity<Void> reorderRoots(@Valid @RequestBody ReorderRequest request) {
        departmentService.reorder(null, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(ApiPath.REORDER_BY_PARENT)
    @Operation(summary = "Reorder the direct children of one department")
    public ResponseEntity<Void> reorder(@PathVariable UUID parentId,
                                        @Valid @RequestBody ReorderRequest request) {
        departmentService.reorder(parentId, request);
        return ResponseEntity.noContent().build();
    }
}
