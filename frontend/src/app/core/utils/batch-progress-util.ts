import { BatchRequestResponse } from "../dtos/batch-request.dto";
import { BatchRequestStatus, BatchTargetType } from "../enums/batch-request.enum";

const FULL_PERCENT = 100;
const SHORT_ID_LENGTH = 8;

// 100% once the batch finished; a cancelled batch keeps the share it reached
export function batchPercent(batch: BatchRequestResponse): number {
    const finished = batch.status === BatchRequestStatus.Completed
        || batch.status === BatchRequestStatus.CompletedWithErrors;
    if (finished || !batch.totalCount) {
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

// "#3f2a9c1e": enough to tell batches apart on screen
export function batchShortId(id: string): string {
    return id.slice(0, SHORT_ID_LENGTH);
}