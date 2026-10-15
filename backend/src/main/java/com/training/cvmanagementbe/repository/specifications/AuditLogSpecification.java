package com.training.cvmanagementbe.repository.specifications;

import com.training.cvmanagementbe.dto.request.audit.AuditLogFilterRequest;
import com.training.cvmanagementbe.entity.models.AuditLog;
import com.training.cvmanagementbe.enums.audit.AuditLogField;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Dynamic WHERE clause for the audit list; an empty filter matches every row
public final class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLog> matching(AuditLogFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.actorId() != null) {
                predicates.add(cb.equal(root.get(AuditLogField.ACTOR_ID.getProperty()), filter.actorId()));
            }
            if (filter.action() != null) {
                predicates.add(cb.equal(root.get(AuditLogField.ACTION.getProperty()), filter.action()));
            }
            if (filter.targetType() != null) {
                predicates.add(cb.equal(root.get(AuditLogField.TARGET_TYPE.getProperty()), filter.targetType()));
            }
            if (filter.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.<LocalDateTime>get(AuditLogField.CREATED_AT.getProperty()),
                        filter.fromDate().atStartOfDay()
                ));
            }
            // Exclusive start of the next day keeps the whole last day, fractions of a second included
            if (filter.toDate() != null) {
                predicates.add(cb.lessThan(
                        root.<LocalDateTime>get(AuditLogField.CREATED_AT.getProperty()),
                        filter.toDate().plusDays(1).atStartOfDay()
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
