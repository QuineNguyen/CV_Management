import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { EnumLabelPipe } from "../../../../utils/enum-label.util";
import { AuditLogService } from "../../../../services/audit-log.service";
import { ToastService } from "../../../../services/toast.service";
import { ROLE_LABELS } from "../../../../models/user.model";
import { AuditActionTone, AuditValueSide } from "../../../../enums/audit-log.enum";
import { AuditLogDetailResponse } from "../../../../dtos/audit-log.dto";
import { actionToneOf, diffTopLevel, prettyValue } from "../../../../utils/audit-log.util";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

/*
 * One audit entry: who, when, on what and the old value next to the new one.
 * - Rendered inline by the page, not via MatDialog and loads its own data by id.
 * - Values are shown pretty-printed when they are JSON; changed top-level fields are listed
 * first so a large snapshot does not have to be read line by line.
 * - On phones the two values do not fit side by side, so a toggle shows one at a time.
 */
@Component({
    selector: 'app-audit-log-detail-dialog',
    standalone: true,
    imports: [DatePipe, EnumLabelPipe],
    templateUrl: './audit-log-detail-dialog.component.html',
    styleUrl: './audit-log-detail-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditLogDetailDialogComponent implements OnInit {

    private static readonly CLOSE_DELAY_MS = 500;
    
    private readonly auditLogService = inject(AuditLogService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly logId = input.required<string>();
    readonly closed = output<void>();

    readonly roleLabels = ROLE_LABELS;
    readonly valueSide = AuditValueSide;

    readonly detail = signal<AuditLogDetailResponse | null>(null);
    readonly loadFailed = signal(false);
    readonly isClosing = signal(false);
    readonly visibleSide = signal<AuditValueSide>(AuditValueSide.New);

    readonly oldText = computed(() => prettyValue(this.detail()?.oldValue ?? null));
    readonly newText = computed(() => prettyValue(this.detail()?.newValue ?? null));
    readonly diffs = computed(() => {
        const entry = this.detail();
        return entry ? diffTopLevel(entry.oldValue, entry.newValue) : [];
    });
    readonly tone = computed<AuditActionTone>(() => actionToneOf(this.detail()?.action ?? ''));

    private backdropMouseDownTarget: EventTarget | null = null;

    ngOnInit(): void {
        this.auditLogService.getById(this.logId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: entry => this.detail.set(entry),
                // The interceptor already showed why
                error: () => this.loadFailed.set(true),
            });
    }

    showSide(side: AuditValueSide): void {
        this.visibleSide.set(side);
    }

    async copyTargetId(): Promise<void> {
        const entry = this.detail();
        if (!entry) {
            return;
        }
        try {
            await navigator.clipboard.writeText(entry.targetId);
            this.toast.success('Target ID copied');
        } catch {
            this.toast.error('Could not copy. Select the ID and copy it manually');
        }
    }

    // ---------- Closing ----------

    close(): void {
        if (this.isClosing()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.closed.emit(), AuditLogDetailDialogComponent.CLOSE_DELAY_MS);
    }

    onBackdropMouseDown(event: MouseEvent): void {
        this.backdropMouseDownTarget = event.target;
    }

    // Only a click that starts and ends on the backdrop closes it; selecting text does not
    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.backdropMouseDownTarget == event.currentTarget) {
            this.close();
        }
        this.backdropMouseDownTarget = null;
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.close();
    }
}