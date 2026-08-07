package com.training.cvmanagementbe.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Jackson JSON configuration for API payloads and stored CV documents.
 *
 * <p>Key configurations:
 * <ul>
 *   <li><b>Timezone & Dates:</b> Uses local timezone (Asia/Ho_Chi_Minh) and ISO-8601 date formats.</li>
 *   <li><b>Schema Evolution:</b> Ignores unknown properties to ensure backward compatibility for stored CV JSON snapshots.</li>
 *   <li><b>Null Preservation:</b> Retains null fields to maintain consistent document structure across version diffs.</li>
 *   <li><b>Encoding & Order:</b> Preserves UTF-8 encoding (supporting Vietnamese/Japanese text) and map key ordering.</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                .timeZone(TimeZone.getTimeZone(ZONE))
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .featuresToEnable(
                        DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .serializationInclusion(JsonInclude.Include.ALWAYS);
    }

}
