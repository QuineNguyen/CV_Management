import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal, viewChild } from "@angular/core";
import { CvContentEditorComponent } from "../cv-content-editor/cv-content-editor.component";
import { CvService } from "../../../services/cv.service";
import { AuthService } from "../../../services/auth.service";
import { ActivatedRoute, Router } from "@angular/router";
import { ToastService } from "../../../services/toast.service";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { DRAFT_STATUS_LABELS, LOCKED_DRAFT_STATUSES } from "../../../enums/draft-status.enum";
import { CvDetailResponse } from "../../../dtos/cv.dto";
import { UserRole } from "../../../enums/user-role.enum";
import { CvContent, emptyCvContent } from "../../../models/cv-content.model";
import { AppRoute } from "../../../enums/app-route.enum";
import { HasUnsavedChanges } from "../../../services/unsaved-changes.guard";

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
    imports: [CvContentEditorComponent],
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

    private readonly editor = viewChild(CvContentEditorComponent);

    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly draftStatusLabels = DRAFT_STATUS_LABELS;

    readonly detail = signal<CvDetailResponse | null>(null);
    readonly loading = signal(true);
    readonly saving = signal(false);
    readonly dirty = signal(false);

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
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
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

        this.cvService.edit(detail.cv.id, { content: editor.toContent() }).subscribe({
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
        this.dirty.set(dirty);
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
}