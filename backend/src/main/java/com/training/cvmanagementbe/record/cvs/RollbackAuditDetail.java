package com.training.cvmanagementbe.record.cvs;

// "After" side of the rollback audit entry; "before" is the number that was current
public record RollbackAuditDetail(
        int sourceVersionNumber,
        int newVersionNumber
) {
}
