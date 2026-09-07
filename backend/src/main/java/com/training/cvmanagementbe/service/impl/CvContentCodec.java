package com.training.cvmanagementbe.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.CvSectionKey;
import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.CvContent;
import com.training.cvmanagementbe.record.PersonalInfo;
import com.training.cvmanagementbe.record.RepeatedEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

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

    /*
     * Structural keys of a repeated entry. item_id is identity, not content: "the item id changed"
     * is not a change a reader of the change log can act on and reporting it would drown the real
     * edits.
     */
    private static final Set<String> IDENTITY_KEYS = Set.of("item_id");

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

    // Enum constant names map 1:1 onto the JSON property names of CvContent.
    private String jsonKeyOf(CvSectionKey sectionKey) {
        return sectionKey.key();
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
}
