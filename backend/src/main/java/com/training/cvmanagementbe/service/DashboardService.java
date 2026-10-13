package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.response.dashboard.AdminDashboardResponse;
import com.training.cvmanagementbe.dto.response.dashboard.EmployeeDashboardResponse;
import com.training.cvmanagementbe.dto.response.dashboard.TechLeadDashboardResponse;

// Pre-computed home-screen figures, one view per role.
public interface DashboardService {

    // Admin and HR: Company-wide figures and both overdue widgets
    AdminDashboardResponse getAdminDashboard();

    // Tech lead: Scope is every member of the teams they lead
    TechLeadDashboardResponse getTechLeadDashboard();

    // Any signed-in user: Their own CVs and the requests sent to them
    EmployeeDashboardResponse getEmployeeDashboard();
}
