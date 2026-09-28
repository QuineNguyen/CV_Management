package com.training.cvmanagementbe.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.AnchoredNote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * Serialises update_requests.anchored_notes.
 * - The entity keeps the raw JSON string, as cv_versions.content_json does; every read and write
 * of the column goes through here, the same role CvContentCodec plays for CV content.
 * - No notes is stored as NULL, not "[]": the column is optional and NULL reads as "none given".
 */
@Component
@RequiredArgsConstructor
public class AnchoredNoteCodec {

    private static final TypeReference<List<AnchoredNote>> NOTE_LIST = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String write(List<AnchoredNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(notes);
        } catch (JsonProcessingException e) {
            throw new ApiException.BusinessRuleException(ErrorCode.VALIDATION_FAILED);
        }
    }

    public List<AnchoredNote> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, NOTE_LIST);
        } catch (JsonProcessingException e) {
            // A stored value that no longer parses is a data defect, not a caller mistake
            throw new IllegalStateException("Stored anchored notes are not valid JSON", e);
        }
    }
}
