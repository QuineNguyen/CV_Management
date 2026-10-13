package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.dto.response.dashboard.AdminDashboardResponse;
import com.training.cvmanagementbe.dto.response.dashboard.EmployeeDashboardResponse;
import com.training.cvmanagementbe.dto.response.dashboard.TechLeadDashboardResponse;
import com.training.cvmanagementbe.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPath.DASHBOARD)
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Pre-computed home-screen figures per role")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping(ApiPath.DASHBOARD_ADMIN)
    @Operation(summary = "Company-wide dashboard with overdue request and approvals (Admin/HR)")
    public ResponseEntity<AdminDashboardResponse> getAdminDashboard() {
        return ResponseEntity.ok(dashboardService.getAdminDashboard());
    }

    @GetMapping(ApiPath.DASHBOARD_TECH_LEAD)
    @Operation(summary = "Dashboard of the teams the caller leads (Tech Lead)")
    public ResponseEntity<TechLeadDashboardResponse> getTechLeadDashboard() {
        return ResponseEntity.ok(dashboardService.getTechLeadDashboard());
    }

    @GetMapping(ApiPath.DASHBOARD_EMPLOYEE)
    @Operation(summary = "Personal dashboard: own CVs and requests sent to the caller")
    public ResponseEntity<EmployeeDashboardResponse> getEmployeeDashboard() {
        return ResponseEntity.ok(dashboardService.getEmployeeDashboard());
    }
}
