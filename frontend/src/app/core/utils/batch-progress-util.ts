import { BatchRequestResponse } from "../dtos/batch-request.dto";
import { BatchRequestStatus, BatchTargetType } from "../enums/batch-request.enum";

const FULL_PERCENT = 100;

// 100% once the batch is no longer PROCESSING
export function batchPercent(batch: BatchRequestResponse): number {
    if (batch.status !== BatchRequestStatus.Processing || !batch.totalCount) {
        return FULL_PERCENT;
    }
    return Math.min(FULL_PERCENT, Math.round((batch.processedCount / batch.totalCount) * FULL_PERCENT));
}

// "KTCN, sub-departments included" / team name / "N selected employee(s)"
export function batchTargetText(batch: BatchRequestResponse): string {
    return batch.targetType === BatchTargetType.Department
        ? `${batch.targetLabel}, sub-departments included`
        : batch.targetLabel;
}