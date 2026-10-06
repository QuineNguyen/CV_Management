package com.training.cvmanagementbe.repository.specifications;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

/*
 * Building blocks for the update request list.
 * - Each factory returns null when its filter is absent; Specification.allOf skips nulls.
 */
public final class UpdateRequestSpecifications {

    // JPA attribute names of UpdateRequest and User
    private static final String EMPLOYEE_ID = "employeeId";
    private static final String EMPLOYEE = "employee";
    private static final String PRIMARY_DEPARTMENT_ID = "primaryDepartmentId";
    private static final String STATUS = "status";
    private static final String LANGUAGE = "language";
    private static final String CREATED_AT = "createdAt";

    private UpdateRequestSpecifications() {

    }

    // Role scope. Null means unrestricted (Admin/HR); never empty, the caller is always included
    public static Specification<UpdateRequest> employeeIn(Collection<UUID> employeeIds) {
        return employeeIds == null
                ? null
                : (root, query, cb) -> root.get(EMPLOYEE_ID).in(employeeIds);
    }

    public static Specification<UpdateRequest> hasStatus(RequestStatus status) {
        return status == null
                ? null
                : (root, query, cb) -> cb.equal(root.get(STATUS), status);
    }

    public static Specification<UpdateRequest> hasLanguage(Language language) {
        return language == null
                ? null
                : (root, query, cb) -> cb.equal(root.get(LANGUAGE), language);
    }

    // Department of the employee who received the request, by their primary department
    public static Specification<UpdateRequest> inDepartment(UUID departmentId) {
        return departmentId == null
                ? null
                : (root, query, cb) -> cb.equal(root.join(EMPLOYEE).get(PRIMARY_DEPARTMENT_ID), departmentId);
    }

    // Whole days: from 00:00 of fromDate
    public static Specification<UpdateRequest> createdFrom(LocalDate fromDate) {
        return fromDate == null
                ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(
                        root.<LocalDateTime>get(CREATED_AT), fromDate.atStartOfDay()
        );
    }

    // Whole days: up to, not including, 00:00 of the day after toDate
    public static Specification<UpdateRequest> createdTo(LocalDate toDate) {
        return toDate == null
                ? null
                : (root, query, cb) -> cb.lessThan(
                        root.<LocalDateTime>get(CREATED_AT), toDate.plusDays(1).atStartOfDay()
        );
    }
}
