package com.training.cvmanagementbe.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.training.cvmanagementbe.constant.CvContentKeys;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.PersonalInfo;
import com.training.cvmanagementbe.record.cvs.RepeatedEntry;
import com.training.cvmanagementbe.record.cvs.SkillRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/*
 * Serialises CV content and exposes the flat views the diff needs.
 *
 * - CvContent is a published version stores a full snapshot and is never rewritten.
 * Everything that reads or writes (content_json) goes through here, so the
 * places that must stay backward compatible are one file rather than scattered.
 */
@Component
@RequiredArgsConstructor
public class CvContentCodec {

    private static final String ITEM_ID = "item_id";
    private static final String DISPLAY_ORDER = "display_order";
    private static final String IS_UNTRANSLATED = "is_untranslated";
    private static final String DELETED_IN_MASTER = "deleted_in_master";

    // Keys describing an entry's place and sync state, not what the owner typed.
    private static final Set<String> STRUCTURAL_KEYS = Set.of(
            ITEM_ID, DISPLAY_ORDER, IS_UNTRANSLATED, DELETED_IN_MASTER, CvContentKeys.SKILL_ID
    );

    /*
     * Structural keys of a repeated entry. item_id is identity, not content: "the item id changed"
     * is not a change a reader of the change log can act on and reporting it would drown the real
     * edits.
     * skill_id is a catalogue reference: Linking an entry to the catalogue is not an edit either
     * and a real change of skill already shows as a change of its name.
     */
    private static final Set<String> IDENTITY_KEYS = Set.of(ITEM_ID, CvContentKeys.SKILL_ID);

    private final ObjectMapper objectMapper;

    // ---------- Serialisation ----------

    public String write(CvContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new ApiException.BusinessRuleException(ErrorCode.VALIDATION_FAILED);
        }
    }

    public CvContent read(String contentJson) {
        try {
            return objectMapper.readValue(contentJson, CvContent.class);
        } catch (JsonProcessingException e) {
            // A stored snapshot that no longer parses is a data defect, not a caller mistake.
            throw new IllegalStateException("Stored CV content is not valid JSON", e);
        }
    }

    // ---------- Template ----------
    /*
     * Content for a brand-new CV: the standard skeleton with personal info seeded from the user
     * account, which then becomes an independent snapshot.
     */
    public CvContent seedFrom(User owner) {
        return applyPersonalInfoSnapshot(CvContent.empty(), owner);
    }

    /*
     * Overwrites only the two identity fields the system owns; anything the employee already typed
     * into personal info survives.
     */
    public CvContent applyPersonalInfoSnapshot(CvContent content, User owner) {
        PersonalInfo existing = content.personalInfo();

        PersonalInfo seeded = new PersonalInfo(
                owner.getFullName(),
                existing.dateOfBirth(),
                owner.getEmail(),
                existing.phone(),
                existing.address(),
                existing.position()
        );

        return new CvContent(
                seeded,
                content.careerObjective(),
                content.skills(),
                content.experience(),
                content.education(),
                content.certifications(),
                content.projects(),
                content.languages(),
                content.additionalInfo()
        );
    }

    /*
     * Content for a new language CV of a profile that already has a master
     * - personal info copied once from the master, never synced afterward;
     * name and email re-taken from the account like every new CV;
     * - career objective starts empty;
     * - each repeated entry becomes a placeholder keeping item_id and display_order,
     * flagged untranslated, with no content. Entries removed in the master are skipped.
     * Tree-based for the same reason as normaliseItemIds.
     */
    public CvContent scaffoldTranslation(CvContent master, User owner) {
        ObjectNode source = objectMapper.valueToTree(master);
        ObjectNode root = objectMapper.createObjectNode();
        root.set(jsonKeyOf(CvSectionKey.PERSONAL_INFO), source.get(jsonKeyOf(CvSectionKey.PERSONAL_INFO)));

        for (CvSectionKey sectionKey : CvSectionKey.values()) {
            if (!sectionKey.repeated()) {
                continue;
            }
            ArrayNode placeholders = root.putArray(jsonKeyOf(sectionKey));

            for (JsonNode entry : source.path(jsonKeyOf(sectionKey))) {
                if (entry.path(DELETED_IN_MASTER).asBoolean(false)) {
                    continue;
                }
                placeholders.addObject()
                        .put(ITEM_ID, entry.path(ITEM_ID).asText(null))
                        .put(DISPLAY_ORDER, entry.path(DISPLAY_ORDER).asInt())
                        .put(IS_UNTRANSLATED, true)
                        .put(DELETED_IN_MASTER, false);
            }
        }
        return applyPersonalInfoSnapshot(objectMapper.convertValue(root, CvContent.class), owner);
    }

    // ---------- Normalisation ----------
    /*
     * Fills in a stable item_id for every repeated entry that arrived without one and
     * rejects duplicates.
     *
     * - The client generates ids when it adds an item, but an entry that slips through with a
     * blank id disappears from every later diff and gives inline comments nothing to anchor to.
     * Two entries sharing an id is worse: the diff silently drops one of them and a
     * comment lands on whichever the sync happens to match first.
     * - Done on the JSON tree rather than by rebuilding each record: the seven entry types would
     * otherwise need a switch and seven constructors, each of which breaks the day a field is added.
     */
    public CvContent normaliseItemIds(CvContent content) {
        ObjectNode root = objectMapper.valueToTree(content);
        Set<String> seen = new HashSet<>();

        for (CvSectionKey sectionKey : CvSectionKey.values()) {
            if (!sectionKey.repeated()) {
                continue;
            }
            JsonNode section = root.get(jsonKeyOf(sectionKey));
            if (section == null || !section.isArray()) {
                continue;
            }

            for (JsonNode entry : section) {
                String itemId = RepeatedEntry.ensureItemId(entry.path(ITEM_ID).asText(null));

                if (!seen.add(itemId)) {
                    throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_CV_ITEM_ID);
                }
                ((ObjectNode) entry).put(ITEM_ID, itemId);
            }
        }
        return objectMapper.convertValue(root, CvContent.class);
    }

    /*
     * Clears is_untranslated on placeholders the owner has started filling in,
     * so untranslatedItemCount only counts entries still waiting.
     */
    public CvContent normaliseTranslationFlags(CvContent content) {
        ObjectNode root = objectMapper.valueToTree(content);
        boolean changed = false;

        for (CvSectionKey sectionKey : CvSectionKey.values()) {
            if (!sectionKey.repeated()) {
                continue;
            }
            for (JsonNode entry : root.path(jsonKeyOf(sectionKey))) {
                if (entry.path(IS_UNTRANSLATED).asBoolean(false) && hasContent(entry)) {
                    ((ObjectNode) entry).put(IS_UNTRANSLATED, false);
                    changed = true;
                }
            }
        }
        return changed ? objectMapper.convertValue(root, CvContent.class) : content;
    }

    // Every item_id present in the content, across all seven repeated sections.
    public Set<String> itemIdsOf(CvContent content) {
        if (content == null) {
            return Set.of();
        }
        return content.repeatedSections().values().stream()
                .flatMap(List::stream)
                .map(RepeatedEntry::itemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Enum constant names map 1:1 onto the JSON property names of CvContent.
    private String jsonKeyOf(CvSectionKey sectionKey) {
        return sectionKey.key();
    }

    // ---------- Skill catalogue references ----------

    // skill_id and typed name of every skills entry, in CV order
    public List<SkillRef> skillRefsOf(CvContent content) {
        if (content == null) {
            return List.of();
        }
        JsonNode section = objectMapper.valueToTree(content).get(jsonKeyOf(CvSectionKey.SKILLS));
        if (section == null || !section.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(section.spliterator(), false)
                .map(this::skillRefOf)
                .toList();
    }

    /*
     * Writes back whatever the resolver returns as each entry's skill_id; nothing else is touched.
     * Tree-based for the same reason as normaliseItemIds.
     */
    public CvContent mapSkillIds(CvContent content, Function<SkillRef, UUID> resolver) {
        ObjectNode root = objectMapper.valueToTree(content);
        JsonNode section = root.get(jsonKeyOf(CvSectionKey.SKILLS));
        if (section == null || !section.isArray()) {
            return content;
        }

        for (JsonNode entry : section) {
            UUID skillId = resolver.apply(skillRefOf(entry));
            if (skillId == null) {
                ((ObjectNode) entry).putNull(CvContentKeys.SKILL_ID);
            } else {
                ((ObjectNode) entry).put(CvContentKeys.SKILL_ID, skillId.toString());
            }
        }
        return objectMapper.convertValue(root, CvContent.class);
    }

    /*
     * Overwrites each linked entry's name with the catalogue name from the map; unlinked entries
     * and ids missing from the map keep the name they were saved with.
     */
    public CvContent mapSkillNames(CvContent content, Map<UUID, String> namesById) {
        if (content == null || namesById.isEmpty()) {
            return content;
        }
        ObjectNode root = objectMapper.valueToTree(content);
        JsonNode section = root.get(jsonKeyOf(CvSectionKey.SKILLS));
        if (section == null || !section.isArray()) {
            return content;
        }

        for (JsonNode entry : section) {
            UUID skillId = parseUuid(entry.path(CvContentKeys.SKILL_ID).asText(null));
            String name = skillId == null ? null : namesById.get(skillId);
            if (name != null) {
                ((ObjectNode) entry).put(CvContentKeys.SKILL_NAME, name);
            }
        }
        return objectMapper.convertValue(root, CvContent.class);
    }

    private SkillRef skillRefOf(JsonNode entry) {
        return new SkillRef(
                parseUuid(entry.path(CvContentKeys.SKILL_ID).asText(null)),
                entry.path(CvContentKeys.SKILL_NAME).asText(null)
        );
    }

    // A malformed id from the client is treated as "not linked" and re-resolved by name
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- Search support ----------

    // Flattens every text value into one string, so full-text search has something to index.
    public String flattenText(CvContent content) {
        StringBuilder builder = new StringBuilder();
        collectText(objectMapper.valueToTree(content), builder);
        return builder.toString().trim();
    }

    // ---------- Flat views (used by ChangeLogGenerator) ----------

    /*
     * The SINGLE section of a key, as a record - or null for repeated sections.
     * Kept here so the generator never switches on section key itself.
     */
    public Object singleSectionOf(CvContent content, CvSectionKey sectionKey) {
        return switch (sectionKey) {
            case PERSONAL_INFO -> content.personalInfo();
            case CAREER_OBJECTIVE -> content.careerObjective();
            default -> null;
        };
    }

    // item_id -> entry, preserving template order, for one repeated section.
    public Map<String, RepeatedEntry> itemsById(CvContent content, CvSectionKey sectionKey) {
        Map<String, RepeatedEntry> result = new LinkedHashMap<>();
        if (content == null) {
            return result;
        }
        List<? extends RepeatedEntry> entries = content.entriesOf(sectionKey);
        for (RepeatedEntry entry : entries) {
            if (entry.itemId() != null) {
                result.put(entry.itemId(), entry);
            }
        }
        return result;
    }

    /*
     * field_key -> scalar text for one record, using the JSON names so the change log speaks the
     * same vocabulary as the stored snapshot and as inline comments.
     */
    public Map<String, String> fieldsOf(Object record) {
        Map<String, String> result = new LinkedHashMap<>();
        if (record == null) {
            return result;
        }
        Map<String, Object> raw = objectMapper.convertValue(record, new TypeReference<>() {});
        raw.forEach((key, value) -> {
            if (IDENTITY_KEYS.contains(key)) {
                return;
            }
            result.put(key, value == null ? null : String.valueOf(value));
        });
        return result;
    }

    /*
     * Same view as fieldsOf, but list values read "a, b" instead of "[a, b]". Used by the
     * read-time diff, whose values are shown to people; fieldsOf stays as the change log stores it.
     */
    public Map<String, String> readableFieldsOf(Object record) {
        Map<String, String> result = new LinkedHashMap<>();
        if (record == null) {
            return result;
        }
        Map<String, Object> raw = objectMapper.convertValue(record, new TypeReference<>() {});
        raw.forEach((key, value) -> {
            if (IDENTITY_KEYS.contains(key)) {
                return;
            }
            result.put(key, readable(value));
        });
        return result;
    }

    // One-line rendering of an added or removed item, stored as its change-log value.
    public String summarise(Object record) {
        StringBuilder builder = new StringBuilder();
        fieldsOf(record).forEach((key, value) -> {
            if (value == null || value.isBlank()) {
                return;
            }
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }

    // ---------- Private helpers ----------

    private String readable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private void collectText(JsonNode node, StringBuilder builder) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            String text = node.asText();
            if (!text.isBlank()) {
                builder.append(text).append(' ');
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectText(child, builder));
            return;
        }
        // Keys are structural metadata, not content: index the values only.
        node.fields().forEachRemaining(entry -> collectText(entry.getValue(), builder));
    }

    // Any field the owner could have typed; structual keys do not count.
    private boolean hasContent(JsonNode entry) {
        Iterator<Map.Entry<String, JsonNode>> fields = entry.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!STRUCTURAL_KEYS.contains(field.getKey()) && isFilled(field.getValue())) {
                return true;
            }
        }
        return false;
    }

    // Blank text, empty lists, false and zero are what an untouched field serialises as.
    private static boolean isFilled(JsonNode value) {
        if (value == null || value.isNull() || value.isBoolean()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isBlank();
        }
        if (value.isNumber()) {
            return value.asDouble() != 0;
        }
        if (value.isContainerNode()) {
            return StreamSupport.stream(value.spliterator(), false).anyMatch(CvContentCodec::isFilled);
        }
        return true;
    }
}
