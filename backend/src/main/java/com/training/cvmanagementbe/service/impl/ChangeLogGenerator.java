package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.ChangeLogEntry;
import com.training.cvmanagementbe.enums.cvs.ChangeType;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.RepeatedEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/*
 * Diffs two content snapshots along the (section, item, field) coordinate system.
 *
 * - Repeated sections are matched by item_id, never by position - item ids are stable
 * across versions and across the languages of one profile, which is exactly what makes
 * "the third experience entry was edited" distinguishable from "one was removed and another added".
 * - v1 has no predecessor, so every populated field comes out as ADDED.
 */
@Component
@RequiredArgsConstructor
public class ChangeLogGenerator {

    private static final String AVATAR_FIELD = "avatar_image_id";

    private final CvContentCodec codec;

    /*
     * - oldContent: Null for v1
     * - avatar: Ids are compared as a personal_info field, although they live in a column
     */
    public List<ChangeLogEntry> generate(CvContent oldContent,
                                         CvContent newContent,
                                         UUID versionId,
                                         UUID oldAvatarImageId,
                                         UUID newAvatarImageId) {
        List<ChangeLogEntry> entries = new ArrayList<>();

        for (CvSectionKey sectionKey : CvSectionKey.values()) {
            if (sectionKey.repeated()) {
                diffItems(entries, versionId, sectionKey, oldContent, newContent);
            } else {
                // SINGLE sections have no items, so itemId stays null.
                diffFields(entries, versionId, sectionKey, null,
                        codec.fieldsOf(oldContent == null ? null : codec.singleSectionOf(oldContent, sectionKey)),
                        codec.fieldsOf(codec.singleSectionOf(newContent, sectionKey)));
            }
        }

        diffAvatar(entries, versionId, oldAvatarImageId, newAvatarImageId);
        return entries;
    }

    // ---------- Section level ----------

    private void diffItems(List<ChangeLogEntry> entries, UUID versionId, CvSectionKey sectionKey,
                           CvContent oldContent, CvContent newContent) {
        Map<String, RepeatedEntry> oldItems = codec.itemsById(oldContent, sectionKey);
        Map<String, RepeatedEntry> newItems = codec.itemsById(newContent, sectionKey);

        for (Map.Entry<String, RepeatedEntry> entry : newItems.entrySet()) {
            RepeatedEntry previous = oldItems.get(entry.getKey());
            if (previous == null) {
                entries.add(build(versionId, ChangeType.ADDED, sectionKey, entry.getKey(), null,
                        null, codec.summarise(entry.getValue())));
            } else {
                diffFields(entries, versionId, sectionKey, entry.getKey(),
                        codec.fieldsOf(previous), codec.fieldsOf(entry.getValue()));
            }
        }

        for (Map.Entry<String, RepeatedEntry> entry : oldItems.entrySet()) {
            if (!newItems.containsKey(entry.getKey())) {
                entries.add(build(versionId, ChangeType.REMOVED, sectionKey, entry.getKey(), null,
                        codec.summarise(entry.getValue()), null));
            }
        }
    }

    // ---------- Field level ----------

    private void diffFields(List<ChangeLogEntry> entries, UUID versionId, CvSectionKey sectionKey,
                            String itemId, Map<String, String> oldFields, Map<String, String> newFields) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(newFields.keySet());
        keys.addAll(oldFields.keySet());

        for (String fieldKey : keys) {
            String before = normalise(oldFields.get(fieldKey));
            String after = normalise(newFields.get(fieldKey));

            if (Objects.equals(before, after)) {
                continue;
            }

            entries.add(build(versionId, classify(before, after), sectionKey, itemId, fieldKey, before, after));
        }
    }

    private void diffAvatar(List<ChangeLogEntry> entries, UUID versionId, UUID before, UUID after) {
        if (Objects.equals(before, after)) {
            return;
        }

        entries.add(build(versionId,
                classify(before == null ? null : before.toString(), after == null ? null : after.toString()),
                CvSectionKey.PERSONAL_INFO, null, AVATAR_FIELD,
                before == null ? null : before.toString(),
                after == null ? null : after.toString()));
    }

    // ---------- Private helpers ----------

    private ChangeType classify(String before, String after) {
        if (before == null) {
            return ChangeType.ADDED;
        }
        return after == null ? ChangeType.REMOVED : ChangeType.MODIFIED;
    }

    // Blank and absent mean the same thing to a reader, so they must not read as a change.
    private String normalise(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ChangeLogEntry build(UUID versionId, ChangeType type, CvSectionKey sectionKey,
                                 String itemId, String fieldKey, String oldValue, String newValue) {
        ChangeLogEntry entry = new ChangeLogEntry();
        entry.setVersionId(versionId);
        entry.setChangeType(type);
        entry.setSectionKey(sectionKey);
        entry.setItemId(itemId);
        entry.setFieldKey(fieldKey);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        return entry;
    }
}
