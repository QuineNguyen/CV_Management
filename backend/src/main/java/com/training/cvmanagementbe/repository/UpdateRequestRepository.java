package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UpdateRequestRepository extends JpaRepository<UpdateRequest, UUID>,
        JpaSpecificationExecutor<UpdateRequest> {

    /*
     * Languages already holding a request in this status for (employee, profile).
     * - A null profile is its own slot, the same way COALESCE works in uk_pending_key [QĐ-46].
     * - Used only to report skipped languages; the unique index is what enforces BR-19.
     */
    @Query("""
            select r.language from UpdateRequest r
            where r.employeeId = :employeeId
              and r.status = :status
              and ((:profileId is null and r.profileId is null) or r.profileId = :profileId)
            """)
    List<Language> findLanguagesByStatus(@Param("employeeId") UUID employeeId,
                                         @Param("profileId") UUID profileId,
                                         @Param("status") RequestStatus status);
}
