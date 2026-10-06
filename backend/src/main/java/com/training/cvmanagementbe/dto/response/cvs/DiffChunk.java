package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.DiffChunkType;

// EQUAL shows on both sides, DELETE only on the old side, INSERT only on the new side.
public record DiffChunk(
        DiffChunkType type,
        String text
) {
}
