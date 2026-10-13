package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.dto.request.cvs.CvSearchFilterRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.*;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.enums.cvs.CvReadScope;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.record.cvs.CvSearchCriteria;
import com.training.cvmanagementbe.repository.CvSearchRepository;
import com.training.cvmanagementbe.repository.SkillRepository;
import com.training.cvmanagementbe.repository.projection.CvSearchRow;
import com.training.cvmanagementbe.repository.projection.DepartmentLink;
import com.training.cvmanagementbe.service.CvSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/*
 * Two criteria groups, two behaviours:
 * - Organization (name, profile, department, team, language): Read from organization
 * data, so a never-published CV still appears - the version column labels it.
 * - Content (skills, full text): Read the latest published version only, so such a CV drops out.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CvSearchServiceImpl implements CvSearchService {

    // A pasted paragraph must not turn into dozens of clauses
    private static final int MAX_CONTENT_TERMS = 8;

    private final CvSearchRepository cvSearchRepository;
    private final SkillRepository skillRepository;
    private final AvatarUrlResolver avatarUrlResolver;

    @Override
    public PagedResponse<CvSearchItemResponse> search(CvSearchFilterRequest filter, Pageable pageable) {
        Page<CvSearchRow> page = cvSearchRepository.search(criteriaOf(filter), pageable);
        List<CvSearchRow> rows = page.getContent();

        // Resolved for this page only, one query each
        Map<UUID, List<CvSearchTeamTag>> teams = cvSearchRepository.teamsOf(
                rows.stream().map(CvSearchRow::employeeId).collect(Collectors.toSet())
        );
        Map<UUID, String> skillNames = loadSkillNames(rows);

        List<CvSearchItemResponse> content = rows.stream()
                .map(row -> toItem(row, teams, skillNames))
                .toList();
        return PagedResponse.of(page, content);
    }

    @Override
    public CvSearchFiltersResponse getFilterOptions() {
        List<CvSearchFilterOption> teams = cvSearchRepository.teamLinks().stream()
                .map(team -> new CvSearchFilterOption(team.id(), team.code(), team.name(), 0))
                .toList();
        return new CvSearchFiltersResponse(flattenTree(cvSearchRepository.departmentLinks()), teams);
    }

    // ---------- Criteria ----------

    private CvSearchCriteria criteriaOf(CvSearchFilterRequest filter) {
        String profileName = trimToNull(filter.profileName());
        // Naming a profile picks profiles explicitly, so it looks past the primary one
        boolean primaryOnly = !Boolean.TRUE.equals(filter.allProfiles()) && profileName == null;

        return new CvSearchCriteria(
                scopeOf(CurrentActor.requireRole()),
                CurrentActor.requireUserId(),
                trimToNull(filter.employeeName()),
                profileName,
                primaryOnly,
                filter.departmentId() == null ? List.of() : withSubDepartments(filter.departmentId()),
                filter.teamId(),
                filter.language(),
                termsOf(filter.keyword()),
                filter.skillIds() == null
                        ? List.of()
                        : filter.skillIds().stream().filter(Objects::nonNull).distinct().toList()
        );
    }

    private static CvReadScope scopeOf(Role role) {
        return switch (role) {
            case ADMIN, HR -> CvReadScope.ALL;
            case TECH_LEAD -> CvReadScope.LED_TEAMS;
            default -> CvReadScope.OWN;
        };
    }

    // Whitespace-separated terms; quotes typed around a phrase are dropped
    private static List<String> termsOf(String keyword) {
        String text = trimToNull(keyword);
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.replace("\"", " ").split("\\s+"))
                .filter(term -> !term.isBlank())
                .distinct()
                .limit(MAX_CONTENT_TERMS)
                .toList();
    }

    // A parent department covers its whole subtree (the tree has no cycles
    private List<UUID> withSubDepartments(UUID rootId) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (DepartmentLink link : cvSearchRepository.departmentLinks()) {
            if (link.parentId() != null) {
                children.computeIfAbsent(link.parentId(), key -> new ArrayList<>()).add(link.id());
            }
        }

        Set<UUID> result = new LinkedHashSet<>();
        Deque<UUID> pending = new ArrayDeque<>(List.of(rootId));
        while (!pending.isEmpty()) {
            UUID id = pending.poll();
            if (result.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return List.copyOf(result);
    }

    // ---------- Filter options ----------

    // Depth-first in (display_order, name) order, so the dropdown reads as the tree
    private static List<CvSearchFilterOption> flattenTree(List<DepartmentLink> links) {
        Set<UUID> ids = links.stream().map(DepartmentLink::id).collect(Collectors.toSet());
        Map<UUID, List<DepartmentLink>> children = new HashMap<>();
        List<DepartmentLink> roots = new ArrayList<>();

        for (DepartmentLink link : links) {
            if (link.parentId() == null || !ids.contains(link.parentId())) {
                roots.add(link);
            } else {
                children.computeIfAbsent(link.parentId(), key -> new ArrayList<>()).add(link);
            }
        }

        List<CvSearchFilterOption> result = new ArrayList<>();
        roots.forEach(root -> appendBranch(root, 0, children, result));
        return result;
    }

    private static void appendBranch(DepartmentLink link,
                                     int depth,
                                     Map<UUID, List<DepartmentLink>> children,
                                     List<CvSearchFilterOption> result) {
        result.add(new CvSearchFilterOption(link.id(), link.code(), link.name(), depth));
        children.getOrDefault(link.id(), List.of())
                .forEach(child -> appendBranch(child, depth + 1, children, result));
    }

    // ---------- Page enrichment ----------

    // Names come from the catalogue, so a rename shows here at once.
    private Map<UUID, String> loadSkillNames(List<CvSearchRow> rows) {
        Set<UUID> skillIds = rows.stream()
                .flatMap(row -> row.skillIds().stream())
                .collect(Collectors.toSet());

        Map<UUID, String> names = new HashMap<>();
        if (!skillIds.isEmpty()) {
            skillRepository.findAllById(skillIds).forEach(skill -> names.put(skill.getId(), skill.getName()));
        }
        return names;
    }

    private CvSearchItemResponse toItem(CvSearchRow row,
                                        Map<UUID, List<CvSearchTeamTag>> teams,
                                        Map<UUID, String> skillNames) {
        List<CvSearchSkillTag> skills = row.skillIds().stream()
                .filter(skillNames::containsKey)
                .map(id -> new CvSearchSkillTag(id, skillNames.get(id)))
                .toList();

        return new CvSearchItemResponse(
                row.cvId(),
                row.employeeId(),
                row.employeeName(),
                row.employeeEmail(),
                avatarUrlResolver.resolve(row.avatarImageId()),
                row.profileName(),
                row.primaryProfile(),
                row.departmentCode(),
                row.departmentName(),
                teams.getOrDefault(row.employeeId(), List.of()),
                row.language(),
                row.versionNumber() != null,
                row.versionNumber(),
                row.publishedAt(),
                skills
        );
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
