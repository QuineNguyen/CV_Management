package com.training.cvmanagementbe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /*
     * Zone pinned explicitly: sent_date and the send hour must not drift with the host TZ.
     * Tests replace this bean with a fixed clock.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock(@Value("${app.time-zone:Asia/Ho_Chi_Minh}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
