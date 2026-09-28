package com.training.cvmanagementbe.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.AnchoredNote;
import com.training.cvmanagementbe.record.cvs.CvContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
 * Checks that every anchored note points at a place that exists in the given CV content.
 * - An entry is required in a REPEATED section and forbidden in a SINGLE one; the field
 * is optional in both.
 * - A field is valid when the record declares it, not when it has a value: "please add your phone
 * number" is a legitimate note on an empty field.
 */
@Component
@RequiredArgsConstructor
public class AnchoredNoteValidator {

    // Identity of an entry, never a field a note can point at
    private static final String ITEM_ID = "item_id";

    private final CvContentCodec codec;
    private final ObjectMapper objectMapper;

    // Field names per record type; the set of types is fixed, so this stays small
    private final Map<Class<?>, Set<String>> fieldKeysByType = new ConcurrentHashMap<>();

    public void requireValid(CvContent content, List<AnchoredNote> notes) {
        for (AnchoredNote note : notes) {
            if (!exists(content, note)) {
                throw new ApiException.BusinessRuleException(ErrorCode.INVALID_NOTE_ANCHOR);
            }
        }
    }

    private boolean exists(CvContent content, AnchoredNote note) {
        CvSectionKey section = note.sectionKey();

        if (section.repeated()) {
            if (note.itemId() == null) {
                return false;
            }
            Object entry = codec.itemsById(content, section).get(note.itemId());
            return entry != null && fieldExists(entry, note.fieldKey());
        }

        if (note.itemId() != null) {
            return false;
        }
        // A note on the whole SINGLE section needs nothing more
        if (note.fieldKey() == null) {
            return true;
        }
        Object record = codec.singleSectionOf(content, section);
        return record != null && fieldExists(record, note.fieldKey());
    }

    private boolean fieldExists(Object record, String fieldKey) {
        return fieldKey == null || fieldKeysOf(record.getClass()).contains(fieldKey);
    }

    // Declared JSON names, read from the same ObjectMapper that writes content_json
    private Set<String> fieldKeysOf(Class<?> type) {
        return fieldKeysByType.computeIfAbsent(type, key -> objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(key))
                .findProperties()
                .stream()
                .map(BeanPropertyDefinition::getName)
                .filter(name -> !ITEM_ID.equals(name))
                .collect(Collectors.toUnmodifiableSet()));
    }
}
