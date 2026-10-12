package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.common.LikePatterns;
import com.training.cvmanagementbe.dto.request.skills.SkillRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.skills.SkillOptionResponse;
import com.training.cvmanagementbe.dto.response.skills.SkillResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.Skill;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.SkillRef;
import com.training.cvmanagementbe.repository.CvSearchRepository;
import com.training.cvmanagementbe.repository.SkillRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillServiceImpl implements SkillService {

    private static final int CODE_MAX_LENGTH = 50;
    // Room for a "_999" suffix when a generated code is already taken
    private static final int CODE_SUFFIX_ROOM = 4;
    private static final String FALLBACK_CODE = "SKILL";
    // Skills typed into a CV get the default order until Admin/HR places them
    private static final int TYPED_SKILL_DISPLAY_ORDER = 0;

    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final CvSearchRepository cvSearchRepository;
    private final CvContentCodec codec;
    private final AuditLogger auditLogger;

    // ---------- Queries ----------

    @Override
    public PagedResponse<SkillOptionResponse> listOptions(String keyword, Pageable pageable) {
        Page<Skill> page = skillRepository.search(LikePatterns.contains(keyword), pageable);
        return PagedResponse.of(page, page.getContent().stream().map(this::toOption).toList());
    }

    @Override
    public PagedResponse<SkillResponse> list(String keyword, Pageable pageable) {
        requireAdminOrHr();

        Page<Skill> page = skillRepository.search(LikePatterns.contains(keyword), pageable);
        List<Skill> skills = page.getContent();

        // Counted for this page only, in one query
        Map<UUID, Long> usage = cvSearchRepository.countCvsBySkill(skills.stream().map(Skill::getId).toList());
        Map<UUID, String> creators = loadUserNames(skills.stream().map(Skill::getCreatedBy).collect(Collectors.toSet()));

        List<SkillResponse> content = skills.stream()
                .map(skill -> toResponse(skill, usage.getOrDefault(skill.getId(), 0L), creators.get(skill.getCreatedBy())))
                .toList();
        return PagedResponse.of(page, content);
    }

    // ---------- Commands ----------

    @Override
    @Transactional
    public SkillResponse create(SkillRequest request) {
        requireAdminOrHr();

        String code = normaliseCode(request.code());
        String name = normaliseName(request.name());
        if (skillRepository.existsByCode(code)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_SKILL_CODE);
        }
        if (skillRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_SKILL_NAME);
        }

        Skill saved = insert(code, name, request.displayOrder());
        SkillResponse response = toResponse(saved, 0L, resolveUserName(saved.getCreatedBy()));

        auditLogger.record(Action.CREATE_SKILL, TargetType.SKILL, saved.getId(), null, response);
        return response;
    }

    /*
     * All three fields are editable. Versions keep only skill_id, so a rename also changes how every
     * published version shows this skill - an accepted exception to immutability.
     */
    @Override
    @Transactional
    public SkillResponse update(UUID id, SkillRequest request) {
        requireAdminOrHr();

        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("skill", id));

        String code = normaliseCode(request.code());
        String name = normaliseName(request.name());
        if (skillRepository.existsByCodeAndIdNot(code, id)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_SKILL_CODE);
        }
        if (skillRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_SKILL_NAME);
        }

        long cvCount = cvSearchRepository.countCvsBySkill(List.of(id)).getOrDefault(id, 0L);
        String creatorName = resolveUserName(skill.getCreatedBy());
        SkillResponse before = toResponse(skill, cvCount, creatorName);

        skill.setCode(code);
        skill.setName(name);
        skill.setDisplayOrder(request.displayOrder());
        Skill saved = skillRepository.save(skill);

        SkillResponse after = toResponse(saved, cvCount, creatorName);
        auditLogger.record(Action.UPDATE_SKILL, TargetType.SKILL, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public CvContent linkCatalog(CvContent content) {
        List<SkillRef> refs = codec.skillRefsOf(content);
        if (refs.isEmpty()) {
            return content;
        }

        Set<UUID> referenced = refs.stream()
                .map(SkillRef::skillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> existing = skillRepository.findAllById(referenced).stream()
                .map(Skill::getId)
                .collect(Collectors.toSet());

        // Two entries typed "Java" and "java" in one save resolve to one row
        Map<String, UUID> resolvedByName = new HashMap<>();
        return codec.mapSkillIds(content, ref -> resolve(ref, existing, resolvedByName));
    }

    // ---------- Catalogue linking ----------

    /*
     * A reference that still exists is kept, so a catalogue rename never re-creates the old name.
     * The editor clears skill_id when the name is retyped, which sends the entry down the name path.
     */
    private UUID resolve(SkillRef ref, Set<UUID> existing, Map<String, UUID> resolvedByName) {
        if (ref.skillId() != null && existing.contains(ref.skillId())) {
            return ref.skillId();
        }
        String name = normaliseName(ref.name());
        if (name == null) {
            return null;
        }
        return resolvedByName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key ->
                skillRepository.findByNameIgnoreCase(name)
                        .map(Skill::getId)
                        .orElseGet(() -> createTypedSkill(name)));
    }

    // The code is generated, the order is the default; Admin/HR tidy both later
    private UUID createTypedSkill(String name) {
        Skill saved = insert(uniqueCodeFrom(name), name, TYPED_SKILL_DISPLAY_ORDER);
        auditLogger.record(Action.CREATE_SKILL, TargetType.SKILL, saved.getId(), null, toOption(saved));
        return saved.getId();
    }

    private Skill insert(String code, String name, int displayOrder) {
        Skill skill = new Skill();
        skill.setCode(code);
        skill.setName(name);
        skill.setDisplayOrder(displayOrder);
        skill.setCreatedBy(CurrentActor.requireUserId());
        skill.setCreatedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    // ---------- Code generation ----------

    private String uniqueCodeFrom(String name) {
        String base = codeFrom(name);
        String candidate = base;
        for (int suffix = 2; skillRepository.existsByCode(candidate); suffix++) {
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    // "Spring Boot" -> SPRING_BOOT, "C#" -> C_SHARP, "C++" -> C_PLUS_PLUS, "Tiếng Đức" -> TIENG_DUC
    private static String codeFrom(String name) {
        String ascii = Normalizer.normalize(
                name.replace("#", " SHARP ").replace("+", " PLUS "),
                Normalizer.Form.NFD
        )
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        String code = ascii.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        int maxBase = CODE_MAX_LENGTH - CODE_SUFFIX_ROOM;
        if (code.length() > maxBase) {
            code = code.substring(0, maxBase).replaceAll("_+$", "");
        }
        // A name written only in non-Latin script (e.g. Japanese) leaves nothing behind
        return code.isEmpty() ? FALLBACK_CODE : code;
    }

    // ---------- Helpers ----------

    private void requireAdminOrHr() {
        Role role = CurrentActor.requireRole();
        if (role != Role.ADMIN && role != Role.HR) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    private static String normaliseCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    // Trimmed with inner whitespace collapsed; null when blank
    private static String normaliseName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private String resolveUserName(UUID userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse(null);
    }

    private Map<UUID, String> loadUserNames(Set<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), user.getFullName()));
        }
        return names;
    }

    private SkillOptionResponse toOption(Skill skill) {
        return new SkillOptionResponse(skill.getId(), skill.getCode(), skill.getName());
    }

    private SkillResponse toResponse(Skill skill, long cvCount, String createdByName) {
        return new SkillResponse(
                skill.getId(),
                skill.getCode(),
                skill.getName(),
                skill.getDisplayOrder(),
                createdByName,
                skill.getCreatedAt(),
                cvCount
        );
    }
}
