package com.training.cvmanagementbe.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Reads and writes batch_requests.target_value: A JSON array of ids, as picked
@Component
@RequiredArgsConstructor
public class BatchTargetCodec {

    private static final TypeReference<List<UUID>> IDS = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String write(List<UUID> targetIds) {
        try {
            return objectMapper.writeValueAsString(targetIds);
        } catch (JsonProcessingException e) {
            throw new ApiException.BusinessRuleException(ErrorCode.VALIDATION_FAILED);
        }
    }

    public List<UUID> read(String targetValue) {
        try {
            return objectMapper.readValue(targetValue, IDS);
        } catch (JsonProcessingException e) {
            // A stored value that no longer parses is a data defect, not a caller mistake
            throw new IllegalStateException("Stored batch target is not valid JSON", e);
        }
    }
}
