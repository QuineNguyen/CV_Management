package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.skills.SkillRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.skills.SkillOptionResponse;
import com.training.cvmanagementbe.dto.response.skills.SkillResponse;
import com.training.cvmanagementbe.enums.skills.SkillSortField;
import com.training.cvmanagementbe.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Skills", description = "Shared skill catalogue - add and edit only, never delete")
public class SkillController {

    // Catalogue order used by every picker
    private static final Sort CATALOGUE_ORDER = Sort.by(SkillSortField.DISPLAY_ORDER.getProperty())
            .and(Sort.by(SkillSortField.NAME.getProperty()));

    private final SkillService skillService;

    @GetMapping(ApiPath.SKILLS + ApiPath.OPTIONS)
    @Operation(summary = "Look up skills by name or code, in catalogue order")
    public ResponseEntity<PagedResponse<SkillOptionResponse>> listOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                CATALOGUE_ORDER
        );
        return ResponseEntity.ok(skillService.listOptions(keyword, pageable));
    }

    @GetMapping(ApiPath.SKILLS)
    @Operation(summary = "List the catalogue with creator and usage count (Admin/HR)")
    public ResponseEntity<PagedResponse<SkillResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "DISPLAY_ORDER") SkillSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        Sort sort = Sort.by(direction, sortBy.getProperty());
        // Equal display orders still read alphabetically
        if (sortBy != SkillSortField.NAME) {
            sort.and(Sort.by(SkillSortField.NAME.getProperty()));
        }

        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);
        return ResponseEntity.ok(skillService.list(keyword, pageable));
    }

    @PostMapping(ApiPath.SKILLS)
    @Operation(summary = "Add a skill to the catalogue (Admin/HR)")
    public ResponseEntity<SkillResponse> create(@Valid @RequestBody SkillRequest request) {
        SkillResponse created = skillService.create(request);
        return ResponseEntity
                .created(URI.create(ApiPath.SKILLS + "/" + created.id()))
                .body(created);
    }

    @PutMapping(ApiPath.SKILLS + ApiPath.BY_ID)
    @Operation(summary = "Edit code, name or display order; a rename shows in every CV using it (Admin/HR)")
    public ResponseEntity<SkillResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(skillService.update(id, request));
    }
}
