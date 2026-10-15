package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.dto.request.configs.UpdateSystemConfigRequest;
import com.training.cvmanagementbe.dto.response.configs.SystemConfigResponse;
import com.training.cvmanagementbe.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "System configuration", description = "Reminder schedule and approval SLA (Admin)")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping(ApiPath.SYSTEM_CONFIGS)
    @Operation(summary = "Read the reminder and SLA settings (Admin)")
    public ResponseEntity<SystemConfigResponse> get() {
        return ResponseEntity.ok(systemConfigService.getConfigs());
    }

    @PutMapping(ApiPath.SYSTEM_CONFIGS)
    @Operation(summary = "Save the reminder and SLA settings; a new SLA applies only to assignments created afterwards (Admin)")
    public ResponseEntity<SystemConfigResponse> update(@Valid @RequestBody UpdateSystemConfigRequest request) {
        return ResponseEntity.ok(systemConfigService.updateConfigs(request));
    }
}
