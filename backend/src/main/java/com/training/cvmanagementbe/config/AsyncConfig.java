package com.training.cvmanagementbe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/*
 * Enables @Async on the notification listener. Uses Boot's applicationTaskExecutor
 * (spring.task.execution.*): declaring another Executor bean would switch that one off.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
