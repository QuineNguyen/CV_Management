import { ChangeDetectionStrategy, Component, HostListener, inject, input, output, signal } from "@angular/core";
import { BatchRequestService } from "../../../services/batch-request.service";
import { ToastService } from "../../../services/toast.service";
import { BatchCancelResponse, BatchRequestResponse } from "../../../dtos/batch-request.dto";
import { BatchRequestStatus } from "../../../enums/batch-request.enum";

/*
 * Confirms and sends a whole-batch cancel, from the batch list or the batch detail.
 * The dialog shows the result toast itself, so hosts only react to (cancelled) and (closed).
 */
@Component({
    selector: 'app-batch-cancel-dialog',
    standalone: true,
    templateUrl: './batch-cancel-dialog.component.html',
    styleUrl: './batch-cancel-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BatchCancelDialogComponent {

    private static readonly CLOSE_DELAY_MS = 500;

    private readonly batchService = inject(BatchRequestService);
    private readonly toast = inject(ToastService);

    readonly batch = input.required<BatchRequestResponse>();
    readonly cancelled = output<BatchCancelResponse>();
    readonly closed = output<void>();

    readonly processingStatus = BatchRequestStatus.Processing;
    readonly submitting = signal(false);
    readonly isClosing = signal(false);
    private backdropMouseDownTarget: EventTarget | null = null;

    confirm(): void {
        if (this.submitting()) {
            return;
        }
        this.submitting.set(true);

        this.batchService.cancel(this.batch().id).subscribe({
            next: result => {
                this.submitting.set(false);
                this.toast.success(result.cancelledCount
                    ? `Batch cancelled - ${result.cancelledCount} request(s) cancelled, employees notified`
                    : 'Batch cancelled - no email left to send'
                );
                this.cancelled.emit(result);
                this.close();
            },
            // The interceptor explains the refusal: already cancelled or nothing pending
            error: () => {
                this.submitting.set(false);
                this.close();
            },
        });
    }

    close(): void {
        if (this.isClosing() || this.submitting()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.closed.emit(), BatchCancelDialogComponent.CLOSE_DELAY_MS);
    }

    onBackdropMouseDown(event: MouseEvent): void {
        this.backdropMouseDownTarget = event.target;
    }

    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.backdropMouseDownTarget === event.currentTarget) {
            this.close();
        }
        this.backdropMouseDownTarget = null;
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.close();
    }
}