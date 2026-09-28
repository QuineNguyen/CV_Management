package com.training.cvmanagementbe.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.record.cvs.AnchoredNote;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/*
 * anchored_notes <-> List<AnchoredNote>.
 * - No notes is stored as NULL, not "[]": the column is optional and NULL reads as "none given".
 * - A plain ObjectMapper is enough: the payload holds strings and one enum only.
 */
@Converter
public class AnchoredNotesConverter implements AttributeConverter<List<AnchoredNote>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<AnchoredNote>> LIST_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<AnchoredNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(notes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Anchored notes could not be serialised", e);
        }
    }

    @Override
    public List<AnchoredNote> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(MAPPER.readValue(json, LIST_TYPE));
        } catch (JsonProcessingException e) {
            // A stored value that no longer parses is a data defect, not a caller mistake
            throw new IllegalStateException("Stored anchored notes are not valid JSON", e);
        }
    }
}
