import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal, viewChild } from "@angular/core";
import { CvContentEditorComponent } from "../cv-content-editor/cv-content-editor.component";
import { CvService } from "../../../services/cv.service";
import { AuthService } from "../../../services/auth.service";
import { ActivatedRoute, Router } from "@angular/router";
import { ToastService } from "../../../services/toast.service";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { DRAFT_STATUS_LABELS, DraftStatus, LOCKED_DRAFT_STATUSES } from "../../../enums/draft-status.enum";
import { CvDetailResponse } from "../../../dtos/cv.dto";
import { UserRole } from "../../../enums/user-role.enum";
import { CvContent, emptyCvContent } from "../../../models/cv-content.model";
import { AppRoute } from "../../../enums/app-route.enum";
import { HasUnsavedChanges } from "../../../services/unsaved-changes.guard";
import { AvatarUploadComponent } from "../../avatar-upload/avatar-upload.component";
import { AvatarChange } from "../../../models/avatar-change.model";
import { ApprovalService } from "../../../services/approval.service";
import { missingRequiredSections } from "../../../models/approval-queue.model";
import { CV_SECTION_LABELS } from "../../../enums/cv-section-key.enum";

/*
 * Edit CV content. The screen branches on the owner's role, not on anything the user picks:
 *  - Employee (and tech lead) owners write into a draft that still needs both approval levels.
 *  - Admin and HR owners editing their own CV publish a version the moment they save, which is
 *    why that path asks for confirmation and says plainly that no one will review it.
 * A draft under review is read-only for everyone including its owner, so reviewers decide on
 * exactly the content they read.
 */
@Component({
    selector: 'app-cv-edit',
    standalone: true,
    imports: [CvContentEditorComponent, AvatarUploadComponent],
    templateUrl: './cv-edit.component.html',
    styleUrl: './cv-edit.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvEditComponent implements OnInit, HasUnsavedChanges {

    private readonly cvService = inject(CvService);
    private readonly auth = inject(AuthService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly toast = inject(ToastService);
    private readonly approvalService = inject(ApprovalService);

    private readonly editor = viewChild(CvContentEditorComponent);

    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly draftStatusLabels = DRAFT_STATUS_LABELS;
    
    // Only the two statuses a draft can enter the approval flow
    private static readonly SUBMITTABLE_STATUSES: readonly DraftStatus[] = [
        DraftStatus.Draft,
        DraftStatus.Rejected
    ];

    /*
     * An Admin/HR owner publishes directly, so there is nothing to submit. Everyone else sees the
     * button as soon as an open draft exists in a submittable status - including a draft that is
     * still incomplete, because hiding it would leave no way to find out what is missing.
     */
    readonly canSubmitForApproval = computed(() => {
        const status = this.openDraft()?.status;
        return !this.directPublish()
            && !!status
            && CvEditComponent.SUBMITTABLE_STATUSES.includes(status);
    });

    readonly detail = signal<CvDetailResponse | null>(null);
    readonly loading = signal(true);
    readonly saving = signal(false);
    readonly submitting = signal(false);

    private readonly editorDirty = signal(false);
    private readonly avatarDirty = signal(false);
    readonly dirty = computed(() => this.editorDirty() || this.avatarDirty());
    readonly avatarImageId = signal<string | null>(null);
    readonly avatarUrl = signal<string | null>(null);

    readonly confirmOpen = signal(false);
    readonly isConfirmClosing = signal(false);
    private confirmBackdropMouseDownTarget: EventTarget | null = null;

    // Mirrors the backend rule: only an Admin/HR owner skips approval.
    readonly directPublish = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR));

    readonly openDraft = computed(() => this.detail()?.openDraft ?? null);

    readonly locked = computed(() => {
        const status = this.openDraft()?.status;
        return !!status && LOCKED_DRAFT_STATUSES.includes(status);
    });

    // Draft first, published version second, empty skeleton last.
    readonly editorContent = computed<CvContent>(() =>
        this.openDraft()?.content ?? this.detail()?.content ?? emptyCvContent());
    
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
                this.seedAvatar(detail);
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    // Draft first, published version second - the same precedence editorContent() uses, so the
    // photo and the text on screen always come from the same place.
    private seedAvatar(detail: CvDetailResponse): void {
        const draft = detail.openDraft;
        this.avatarImageId.set(draft?.avatarImageId ?? detail.avatarImageId ?? null);
        this.avatarUrl.set(draft?.avatarUrl ?? detail.avatarUrl ?? null);
    }

    onAvatarChanged(change: AvatarChange): void {
        this.avatarImageId.set(change.imageId);
        this.avatarUrl.set(change.presignedUrl);
        this.avatarDirty.set(true);
    }

    /*
     * Removing is allowed here and nowhere else: this request writes avatar_image_id directly, so
     * null reaches the column. The old image stays in the bucket - a published version may still
     * point at it and nothing in this screen can know that.
     */
    removeAvatar(): void {
        if (this.locked() || this.saving()) {
            return;
        }
        this.avatarImageId.set(null);
        this.avatarUrl.set(null);
        this.avatarDirty.set(true);
    }

    // ---------- Save ----------

    onSaveClicked(): void {
        const editor = this.editor();
        if (!editor || this.saving() || this.locked()) {
            return;
        }
        if (!editor.valid) {
            editor.markAllTouched();
            this.toast.error('Fill in the required fields before saving');
            return;
        }
        // Publishing bypasses the whole review flow, so it is never one click away.
        if (this.directPublish()) {
            this.confirmOpen.set(true);
            this.isConfirmClosing.set(false);
            return;
        }
        this.save();
    }

    confirmPublish(): void {
        this.closeConfirm();
        this.save();
    }

    private save(): void {
        const detail = this.detail();
        const editor = this.editor();
        if (!detail || !editor) {
            return;
        }
        this.saving.set(true);

        this.cvService.edit(detail.cv.id, { content: editor.toContent(), avatarImageId: this.avatarImageId(), }).subscribe({
            next: result => {
                this.toast.success(result.directPublish
                    ? `Published as v${result.publishedVersion?.versionNumber}`
                    : 'Draft saved'
                );

                void this.router.navigate(['/' + AppRoute.Cvs, detail.cv.id]);
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Confirm dialog ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.confirmOpen()) {
            this.cancelConfirm();
        }
    }

    onConfirmBackdropMouseDown(event: MouseEvent): void {
        this.confirmBackdropMouseDownTarget = event.target;
    }

    onConfirmBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.confirmBackdropMouseDownTarget === event.currentTarget) {
            this.cancelConfirm();
        }
        this.confirmBackdropMouseDownTarget = null;
    }

    cancelConfirm(): void {
        if (this.isConfirmClosing()) {
            return;
        }
        this.isConfirmClosing.set(true);
        setTimeout(() => this.closeConfirm(), 500);
    }

    private closeConfirm(): void {
        this.confirmOpen.set(false);
        this.isConfirmClosing.set(false);
    }

    // ---------- Misc ----------

    onDirtyChanged(dirty: boolean): void {
        this.editorDirty.set(dirty);
    }

    cancel(): void {
        const id = this.detail()?.cv.id;
        void this.router.navigate(id ? ['/' + AppRoute.Cvs, id] : ['/' + AppRoute.Profiles]);
    }

    // Warns on tab close; in-app navigation away is not intercepted at this stage.
    @HostListener('window:beforeunload', ['$event'])
    onBeforeUnload(event: BeforeUnloadEvent): void {
        if (this.hasUnsavedChanges()) {
            event.preventDefault();
        }
    }

    // A save in flight is not "unsaved": the navigation it triggers must not be blocked.
    hasUnsavedChanges(): boolean {
        return this.dirty() && !this.saving();
    }

    /*
     * Submitting is a one-way door: the content locks the moment this succeeds, so unsaved editor
     * changes must be saved first or they are lost behind the lock.
     */
    onSubmitForApproval(): void {
        const draft = this.openDraft();
        if (!draft || this.submitting() || this.saving()) {
            return;
        }

        if (this.dirty()) {
            this.toast.error('Save your changes before submitting');
            return;
        }

        /*
         * Checked here purely so the toast can name the gaps. The server validates the same three
         * sections and is the authority; this screen already holds the content, so re-deriving the
         * list costs one pass over an object it owns.
         */
        const missing = missingRequiredSections(draft.content);
        if (missing.length) {
            this.toast.error(`Fill in: ${missing.map(key => CV_SECTION_LABELS[key]).join(', ')}`);
            return;
        }

        this.submitting.set(true);

        this.approvalService.submit(draft.id).subscribe({
            next: result => {
                this.toast.success(result.level1Skipped
                    ? 'Submitted - sent straight to HR, since you are the tech lead who would review it'
                    : 'Submitted for tech lead review'
                );

                void this.router.navigate(['/' + AppRoute.Cvs, this.detail()!.cv.id]);
            },
            // 422 and 409 are both rendered by the error interceptor from their code.
            error: () => this.submitting.set(false),
        });
    }
}