package com.training.cvmanagementbe.entity.converter;

import com.training.cvmanagementbe.enums.CvSectionKey;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/*
 * Persists CvSectionKey as its snake_case wire value.
 *
 * - The column is constrained to the same nine keys that appear inside content_json, so
 * a change log row and the snapshot it describes name a section identically. EnumType.STRING would
 * write the constant name instead and every insert would fail the CHECK.
 */
@Converter
public class CvSectionKeyConverter implements AttributeConverter<CvSectionKey, String> {

    @Override
    public String convertToDatabaseColumn(CvSectionKey attribute) {
        return attribute == null ? null : attribute.key();
    }

    @Override
    public CvSectionKey convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Arrays.stream(CvSectionKey.values())
                .filter(key -> key.key().equals(dbData))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown CV section key in database: " + dbData));
    }
}
