import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal } from "@angular/core";
import { CvContentEditorComponent } from "../cv-content-editor/cv-content-editor.component";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { DatePipe } from "@angular/common";
import { CvService } from "../../../services/cv.service";
import { AuthService } from "../../../services/auth.service";
import { ToastService } from "../../../services/toast.service";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { DRAFT_STATUS_LABELS, DraftStatus } from "../../../enums/draft-status.enum";
import { VERSION_SOURCE_LABELS } from "../../../enums/version-source.enum";
import { CvDetailResponse, CvResponse } from "../../../dtos/cv.dto";
import { UserRole } from "../../../enums/user-role.enum";
import { LifecycleStatus } from "../../../enums/lifecycle-status.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { QueryParam } from "../../../enums/query-param.enum";
import { MatTooltipModule } from "@angular/material/tooltip";
import { InlineCommentStatus } from "../../../enums/inline-comment-status.enum";
import { ApprovalService } from "../../../services/approval.service";

/*
 * Read-only view of a CV's current version, plus the actions available on it.
 * - Deleting the master of a profile that still holds other CVs requires naming a successor -
 * the localisation chain has to keep an anchor - so the delete dialog turns into a picker in that
 * case rather than failing with an error after the fact.
 */
@Component({
    selector: 'app-cv-detail',
    standalone: true,
    imports: [CvContentEditorComponent, RouterLink, MatTooltipModule, DatePipe],
    templateUrl: './cv-detail.component.html',
    styleUrl: './cv-detail.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvDetailComponent implements OnInit {

    private readonly cvService = inject(CvService);
    private readonly auth = inject(AuthService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly toast = inject(ToastService);
    private readonly approvalService = inject(ApprovalService);

    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly draftStatusLabels = DRAFT_STATUS_LABELS;
    readonly versionSourceLabels = VERSION_SOURCE_LABELS;

    readonly detail = signal<CvDetailResponse | null>(null);
    readonly siblings = signal<CvResponse[]>([]);
    readonly loading = signal(true);

    readonly deleteOpen = signal(false);
    readonly deleting = signal(false);
    readonly isDeleteClosing = signal(false);
    readonly newMasterCvId = signal<string | null>(null);
    private deleteBackdropMouseDownTarget: EventTarget | null = null;
    
    readonly cancelDraftOpen = signal(false);
    readonly cancellingDraft = signal(false);
    readonly isCancelClosing = signal(false);
    private cancelBackdropMouseDownTarget: EventTarget | null = null;

    // Only the owner writes CV content; there is no path for anyone else.
    readonly canEdit = computed(() => this.auth.user()?.id === this.detail()?.cv.employeeId);

    readonly canDelete = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR));

    /*
     * Review feedback is addressed to the author. HR, Admin and tech leads open this screen to read
     * the official CV, so the rejection banner and the comments stay hidden from them.
     */
    readonly rejectedForOwner = computed(() => this.canEdit() && this.detail()?.openDraft?.status === DraftStatus.Rejected);

    readonly openCommentCount = computed(() => (this.detail()?.openDraft?.inlineComments ?? [])
        .filter(comment => !comment.parentCommentId && comment.status === InlineCommentStatus.Open)
        .length);

    /*
     * Comments anchor to the item ids of the draft, so they are only handed to the editor that
     * renders the draft. Pinning them next to published content would point at the wrong entries.
     * No canReplyToComments: replying belongs in the edit screen, where the fixes are made.
     */
    readonly draftComments = computed(() => this.canEdit() ? this.detail()?.openDraft?.inlineComments ?? [] : []);

    // Other active CVs of the same profile - the candidates for a new master.
    readonly masterCandidates = computed(() => this.siblings()
        .filter(cv => cv.id !== this.detail()?.cv.id && cv.lifecycleStatus === LifecycleStatus.Active));

    readonly needsNewMaster = computed(() =>
        !!this.detail()?.cv.master && this.masterCandidates().length > 0);

    /*
     * An owner drops their own draft only before it enters a review. Once submitted it sits in a
     * named reviewer's queue and withdrawing it there would be an exit from the approval flow that
     * nobody signed off - ask the reviewer to reject it, or an admin to cancel it.
     */
    readonly canCancelDraft = computed(() => {
        const status = this.detail()?.openDraft?.status;
        return this.canEdit() && (status === DraftStatus.Draft || status === DraftStatus.Rejected);
    })

    ngOnInit(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.load(id);
        }
    }

    private load(id: string): void {
        this.cvService.getById(id).subscribe({
            next: detail => {
                this.detail.set(detail);
                this.loading.set(false);
                this.loadSiblings(detail.cv.profileId);
            },
            error: () => this.loading.set(false),
        });
    }

    private loadSiblings(profileId: string): void {
        this.cvService.listByProfile(profileId).subscribe({
            next: cvs => this.siblings.set(cvs),
        });
    }

    edit(): void {
        const id = this.detail()?.cv.id;
        if (id) {
            void this.router.navigate(['/' + AppRoute.Cvs, id, 'edit']);
        }
    }

    // ---------- Delete ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.deleteOpen()) {
            this.cancelDelete();
        } else if (this.cancelDraftOpen()) {
            this.dismissCancelDraft();
        }
    }

    askDelete(): void {
        this.isDeleteClosing.set(false);
        this.newMasterCvId.set(this.masterCandidates()[0]?.id ?? null);
        this.deleteOpen.set(true);
    }

    onDeleteBackdropMouseDown(event: MouseEvent): void {
        this.deleteBackdropMouseDownTarget = event.target;
    }

    onDeleteBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.deleteBackdropMouseDownTarget === event.currentTarget) {
            this.cancelDelete();
        }
        this.deleteBackdropMouseDownTarget = null;
    }

    cancelDelete(): void {
        if (this.isDeleteClosing() || this.deleting()) {
            return;
        }
        this.isDeleteClosing.set(true);
        setTimeout(() => this.closeDelete(), 500);
    }

    selectNewMaster(cvId: string): void {
        this.newMasterCvId.set(cvId);
    }

    confirmDelete(): void {
        const detail = this.detail();
        if (!detail || this.deleting()) {
            return;
        }
        if (this.needsNewMaster() && !this.newMasterCvId()) {
            return;
        }
        this.deleting.set(true);

        const body = this.needsNewMaster() ? { newMasterCvId: this.newMasterCvId() } : undefined;

        this.cvService.delete(detail.cv.id, body).subscribe({
            next: () => {
                this.closeDelete();
                this.toast.success(
                    `${this.languageLabels[detail.cv.language]} CV deleted - it can be restored later`
                );
                void this.router.navigate(['/' + AppRoute.Profiles], {
                    queryParams: detail.cv.employeeId ? { [QueryParam.EmployeeId]: detail.cv.employeeId } : undefined,
                });
            },
            error: () => {
                this.deleting.set(false);
                this.isDeleteClosing.set(false);
            },
        });
    }

    private closeDelete(): void {
        this.deleteOpen.set(false);
        this.deleting.set(false);
        this.isDeleteClosing.set(false);
    }

    // ---------- Cancel draft ----------

    askCancelDraft(): void {
        this.isCancelClosing.set(false);
        this.cancelDraftOpen.set(true);
    }

    onCancelBackdropMouseDown(event: MouseEvent): void {
        this.cancelBackdropMouseDownTarget = event.target;
    }

    onCancelBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.cancelBackdropMouseDownTarget == event.currentTarget) {
            this.dismissCancelDraft();
        }
        this.cancelBackdropMouseDownTarget = null;
    }

    dismissCancelDraft(): void {
        if (this.isCancelClosing() || this.cancellingDraft()) {
            return;
        }
        this.isCancelClosing.set(true);
        setTimeout(() => this.closeCancelDraft(), 500);
    }

    /*
     * No reason is asked for: nobody has seen this draft, so there is nobody to explain it to.
     * The page reloads rather than navigating away - the published CV is still here and unchanged,
     * which is exactly the reassurance the confirmation just promised.
     */
    confirmCancelDraft(): void {
        const detail = this.detail();
        const draftId = detail?.openDraft?.id;
        if (!draftId || !detail || this.cancellingDraft()) {
            return;
        }
        // Capture before the reload: the message must describe the CV as it was discarded.
        const hasPublishedVersion = !!detail.currentVersion;
        this.cancellingDraft.set(true);

        this.approvalService.cancelDraft(draftId).subscribe({
            next: () => {
                this.closeCancelDraft();
                this.toast.success(hasPublishedVersion
                    ? 'Draft discarded — the published version is unchanged'
                    : 'Draft discarded — this CV stays empty until you start a new draft'
                );
                this.load(detail.cv.id);
            },
            error: () => this.closeCancelDraft(),
        });
    }

    private closeCancelDraft(): void {
        this.cancelDraftOpen.set(false);
        this.cancellingDraft.set(false);
        this.isCancelClosing.set(false);
    }
}