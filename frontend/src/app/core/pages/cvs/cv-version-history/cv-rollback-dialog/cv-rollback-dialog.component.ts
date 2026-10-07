import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, input, output, signal } from "@angular/core";
import { CvService } from "../../../../services/cv.service";
import { ToastService } from "../../../../services/toast.service";
import { CvVersionHistoryItem } from "../../../../dtos/cv-version.dto";
import { DRAFT_STATUS_LABELS, DraftStatus } from "../../../../enums/draft-status.enum";

/*
 * Confirms a rollback before it publishes anything.
 * - States which version comes back and which number it gets and that author and reviewers are
 * inherited from the source: the person clicking is never written onto the version.
 * - Failures (draft under review, stale timeline) are named by the error interceptor; the
 * dialog only reports that it failed so the page can reload what the server has.
 */
@Component({
    selector: 'app-cv-rollback-dialog',
    standalone: true,
    imports: [DatePipe],
    templateUrl: './cv-rollback-dialog.component.html',
    styleUrl: './cv-rollback-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class CvRollbackDialogComponent {

    // Outlasts the 0.18s fade-out of .modal-backdrop.is-closing
    private static readonly CLOSE_DELAY_MS = 200;

    private readonly cvService = inject(CvService);
    private readonly toast = inject(ToastService);

    readonly cvId = input.required<string>();
    readonly target = input.required<CvVersionHistoryItem>();
    readonly currentVersionNumber = input.required<number>();
    // Owner's DRAFT / REJECTED draft, which stays open as it is
    readonly openDraftStatus = input<DraftStatus | null>(null);

    readonly cancelled = output<void>();
    readonly completed = output<CvVersionHistoryItem>();
    readonly failed = output<void>();

    readonly draftStatusLabels = DRAFT_STATUS_LABELS;

    readonly rolling = signal(false);
    readonly isClosing = signal(false);
    private backdropMouseDownTarget: EventTarget | null = null;

    // Preview only; the toast reports the number the server actually assigned
    readonly nextVersionNumber = computed(() => this.currentVersionNumber() + 1);

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.cancel();
    }

    onBackdropMouseDown(event: MouseEvent): void {
        this.backdropMouseDownTarget = event.target;
    }

    // A text selection dragged out of the modal must not close it
    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.backdropMouseDownTarget === event.currentTarget) {
            this.cancel();
        }
        this.backdropMouseDownTarget = null;
    }

    cancel(): void {
        if (this.isClosing() || this.rolling()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.cancelled.emit(), CvRollbackDialogComponent.CLOSE_DELAY_MS);
    }

    confirm(): void {
        if (this.rolling()) {
            return;
        }
        const target = this.target();
        this.rolling.set(true);

        this.cvService.rollback(this.cvId(), { targetVersionId: target.id }).subscribe({
            next: created => {
                this.toast.success(
                    `Rolled back to v${target.versionNumber} : v${created.versionNumber} is now the current version`
                );
                this.completed.emit(created);
            },
            error: () => {
                this.rolling.set(false);
                this.failed.emit();
            },
        });
    }
}