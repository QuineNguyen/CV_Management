import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, HostListener, inject, input, OnInit, output, signal, untracked } from "@angular/core";
import { FormControl, ReactiveFormsModule, Validators } from "@angular/forms";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../../utils/app-date-adapter.util";
import { UpdateRequestService } from "../../../services/update-request.service";
import { UserService } from "../../../services/user.service";
import { CvProfileService } from "../../../services/cv-profile.service";
import { CvService } from "../../../services/cv.service";
import { ToastService } from "../../../services/toast.service";
import { CV_LANGUAGE_LABELS, CV_LANGUAGE_ORDER, CvLanguage, LANGUAGE_SCOPE_LABELS, LanguageScope, UpdateRequestLanguage } from "../../../enums/cv-language.enum";
import { startOfToday, toIsoDate } from "../../../utils/iso-date.util";
import { UserResponse } from "../../../dtos/user.dto";
import { CvProfileResponse } from "../../../dtos/cv-profile.dto";
import { CvResponse } from "../../../dtos/cv.dto";
import { CvContent } from "../../../models/cv-content.model";
import { ROLE_LABELS } from "../../../models/user.model";
import { AnchoredNoteRequest, CreateUpdateRequestResponse } from "../../../dtos/update-request.dto";
import { CvSectionKey } from "../../../enums/cv-section-key.enum";
import { LifecycleStatus } from "../../../enums/lifecycle-status.enum";
import { catchError, debounceTime, defer, distinctUntilChanged, finalize, map, Observable, of, startWith, switchMap } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AccountStatus } from "../../../enums/account-status.enum";
import { AuthService } from "../../../services/auth.service";
import { AnchorPickerComponent } from "../../approvals/anchor-picker/anchor-picker.component";
import { PendingInlineComment } from "../../../models/inline-comment.model";
import { CV_SECTIONS } from "../../../models/cv-section-descriptor.model";
import { isSectionFilled } from "../../../utils/cv-anchor.util";

/*
 * Form for one update request, opened from the list or from CV Detail.
 * - From CV Detail the employee is fixed; profile and language are preselected but stay editable.
 * - Notes anchor into one CV's published content, so they only open up for a single
 * language on a profile whose CV already has a version.
 * - The dialog sends the request and shows the result toast itself, so both hosts only need
 * to react to (created) and (closed).
 */
@Component({
    selector: 'app-create-update-request-dialog',
    standalone: true,
    imports: [ReactiveFormsModule, MatDatepickerModule, MatNativeDateModule, AnchorPickerComponent],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './create-update-request-dialog.component.html',
    styleUrl: './create-update-request-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateUpdateRequestDialogComponent implements OnInit {

    private static readonly SEARCH_DEBOUNCE_MS = 300;
    private static readonly EMPLOYEE_LOOKUP_SIZE = 20;
    private static readonly PROFILE_LOOKUP_SIZE = 100;
    private static readonly CLOSE_DELAY_MS = 500;
    // Same limits as the server-side DTOs
    private static readonly REASON_MAX_LENGTH = 1000;
    private static readonly NOTE_MAX_LENGTH = 1000;

    private readonly updateRequestService = inject(UpdateRequestService);
    private readonly userService = inject(UserService);
    private readonly profileService = inject(CvProfileService);
    private readonly cvService = inject(CvService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly auth = inject(AuthService);

    // Set when opened from CV Detail
    readonly prefillEmployeeId = input<string | null>(null);
    readonly prefillProfileId = input<string | null>(null);
    readonly prefillLanguage = input<CvLanguage | null>(null);
    readonly prefillCvId = input<string | null>(null);

    readonly created = output<void>();
    readonly closed = output<void>();

    readonly roleLabels = ROLE_LABELS;
    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly scopeLabels = LANGUAGE_SCOPE_LABELS;
    readonly allScope = LanguageScope.All;
    readonly languageOptions: readonly UpdateRequestLanguage[] = [...CV_LANGUAGE_ORDER, LanguageScope.All];
    readonly reasonLabel = 'Overall reason';
    readonly reasonMaxLength = CreateUpdateRequestDialogComponent.REASON_MAX_LENGTH;
    readonly noteMaxLength = CreateUpdateRequestDialogComponent.NOTE_MAX_LENGTH;
    readonly minDeadline = startOfToday();

    // ---------- Employee ----------
    readonly employee = signal<UserResponse | null>(null);
    readonly employeeLocked = computed(() => !!this.prefillEmployeeId());
    readonly pickerOpen = signal(false);
    readonly searching = signal(false);
    readonly candidates = signal<UserResponse[]>([]);
    readonly keyword = new FormControl('', { nonNullable: true });

    // ---------- Target slot ----------
    readonly profiles = signal<CvProfileResponse[]>([]);
    readonly profilesLoading = signal(false);
    readonly selectedProfileId = signal<string | null>(null);
    readonly profilePickerOpen = signal(false);
    readonly language = signal<UpdateRequestLanguage | null>(null);
    private readonly profileCvs = signal<CvResponse[]>([]);

    readonly selectedProfileLabel = computed(() => {
        if (!this.employee()) {
            return 'Choose an employee first';
        }
        if (this.profilesLoading()) {
            return 'Loading profiles...';
        }
        const id = this.selectedProfileId();
        if (!id) {
            return 'None: the employee creates a new one';
        }
        const profile = this.profiles().find(p => p.id === id);
        return profile ? `${profile.name}${profile.primary ? ' (primary)' : ''}` : 'None: the employee creates a new one';
    });

    // ---------- Deadline and reason ----------
    readonly deadline = signal<Date | null>(null);
    readonly reason = new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(CreateUpdateRequestDialogComponent.REASON_MAX_LENGTH)],
    });

    // ---------- Notes ----------
    readonly cvContent = signal<CvContent | null>(null);
    readonly contentLoading = signal(false);
    private readonly contentCache = new Map<string, CvContent | null>();
    readonly notes = signal<PendingInlineComment[]>([]);
    readonly notePickerOpen = signal(false);

    readonly submitting = signal(false);
    readonly submitAttempted = signal(false);
    readonly isClosing = signal(false);
    private backdropMouseDownTarget: EventTarget | null = null;

    readonly isSingleLanguage = computed(() => {
        const language = this.language();
        return !!language && language !== LanguageScope.All;
    });

    // The ACTIVE CV the request will point at, when the slot already has one
    readonly targetCvId = computed(() => {
        const language = this.language();
        const profileId = this.selectedProfileId();
        if (!profileId || !language || language === LanguageScope.All) {
            return null;
        }
        const cv = this.profileCvs()
            .find(item => item.language === language && item.lifecycleStatus === LifecycleStatus.Active);
        if (cv) {
            return cv.id;
        }
        // From CV Detail the CV is known before the profile's CV list arrives
        const isPrefilledSlot = profileId === this.prefillProfileId() && language === this.prefillLanguage();
        return isPrefilledSlot ? this.prefillCvId() : null;
    });

    // Same rule the picker uses, so "+ Add note" never opens onto an empty list
    private readonly hasAnchorableContent = computed(() => {
        const content = this.cvContent();
        return !!content && CV_SECTIONS.some(section => isSectionFilled(content, section));
    });

    readonly canAddNotes = computed(() => this.isSingleLanguage() && this.hasAnchorableContent());

    // Why notes cannot be added right now, so the section never disappears without a word
    readonly notesUnavailableReason = computed<string | null>(() => {
        if (!this.language()) {
            return 'Choose a single language to pin notes to the CV.';
        }
        if (!this.isSingleLanguage()) {
            return 'Notes need a single language: each note points into one CV.';
        }
        if (!this.selectedProfileId()) {
            return 'No profile selected: the employee will create a new CV, so there is nothing to point at yet.';
        }
        if (!this.targetCvId()) {
            return 'This profile has no CV in this language yet: the employee will be asked to create it.';
        }
        if (!this.cvContent()) {
            return 'This CV has no published version yet, so there is nothing to point at.';
        }
        if (!this.hasAnchorableContent()) {
            return `This CV is still empty, so there is nothing to point at. Use "${this.reasonLabel}" instead.`;
        }
        return null;
    });

    constructor() {
        // A different target CV means different anchors, so the notes start over
        effect(() => {
            const cvId = this.targetCvId();
            untracked(() => this.switchTargetCv(cvId));
        });
    }

    ngOnInit(): void {
        this.language.set(this.prefillLanguage());

        const employeeId = this.prefillEmployeeId();
        if (employeeId) {
            this.userService.getById(employeeId).subscribe({
                next: user => this.selectEmployee(user, this.prefillProfileId()),
            });
            return;
        }
        this.watchKeyword();
    }

    private closeAllPickers(): void {
        this.pickerOpen.set(false);
        this.profilePickerOpen.set(false);
    }

    // ---------- Employee ----------

    togglePicker(event: MouseEvent): void {
        event.stopPropagation();
        const wasOpen = this.pickerOpen();
        this.closeAllPickers();
        this.pickerOpen.set(!wasOpen);
    }

    pickEmployee(user: UserResponse): void {
        this.closeAllPickers();
        if (this.employee()?.id !== user.id) {
            this.selectEmployee(user, null);
        }
    }

    // ---------- Target slot ----------

    toggleProfilePicker(event: MouseEvent): void {
        event.stopPropagation();
        if (!this.employee() || this.profilesLoading()) {
            return;
        }
        const wasOpen = this.profilePickerOpen();
        this.closeAllPickers();
        this.profilePickerOpen.set(!wasOpen);
    }

    pickProfile(profileId: string | null): void {
        this.profilePickerOpen.set(false);
        this.selectProfile(profileId);
    }

    onProfileChange(value: string): void {
        this.selectProfile(value || null);
    }

    selectLanguage(language: UpdateRequestLanguage): void {
        this.language.set(language);
    }

    languageOptionLabel(option: UpdateRequestLanguage): string {
        return option === LanguageScope.All ? this.scopeLabels[LanguageScope.All] : this.languageLabels[option];
    }

    onDeadlineChange(date: Date | null): void {
        this.deadline.set(date);
    }

    reasonMissing(): boolean {
        return !this.reason.value.trim();
    }

    // ---------- Notes ----------

    toggleNotePicker(): void {
        this.notePickerOpen.update(open => !open);
    }

    addNote(comment: PendingInlineComment): void {
        this.notes.update(list => [...list, comment]);
        this.notePickerOpen.set(false);
    }

    removeNote(localId: string): void {
        this.notes.update(list => list.filter(note => note.localId !== localId));
    }

    // ---------- Submit / close ----------

    submit(): void {
        this.submitAttempted.set(true);
        this.reason.markAsTouched();

        const employee = this.employee();
        const language = this.language();
        const deadline = toIsoDate(this.deadline());
        if (!employee || !language || !deadline || this.reason.invalid || this.reasonMissing() || this.submitting()) {
            return;
        }

        this.submitting.set(true);
        const anchoredNotes: AnchoredNoteRequest[] = this.notes().map(item => ({
            sectionKey: item.request.sectionKey,
            itemId: item.request.itemId,
            fieldKey: item.request.fieldKey,
            note: item.request.content,
        }));

        this.updateRequestService.create({
            employeeId: employee.id,
            profileId: this.selectedProfileId(),
            language,
            reason: this.reason.value.trim(),
            deadline,
            anchoredNotes: anchoredNotes.length ? anchoredNotes : null,
        }).subscribe({
            next: result => {
                this.toast.success(this.resultMessage(result));
                this.submitting.set(false);
                this.created.emit();
                this.close();
            },
            // The interceptor names the rule that refused it; the form stays open to fix it
            error: () => this.submitting.set(false),
        });
    }

    close(): void {
        if (this.isClosing() || this.submitting()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.closed.emit(), CreateUpdateRequestDialogComponent.CLOSE_DELAY_MS);
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

    // Clicks inside the dialog stay inside it and close open pickers on the way
    onModalClick(event: MouseEvent): void {
        event.stopPropagation();
        this.closeAllPickers();
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.pickerOpen()) {
            this.pickerOpen.set(false);
            return;
        }
        if (this.profilePickerOpen()) {
            this.profilePickerOpen.set(false);
            return;
        }
        if (this.notePickerOpen()) {
            this.notePickerOpen.set(false);
            return;
        }
        this.close();
    }

    // ---------- Private helpers ----------

    private watchKeyword(): void {
        this.keyword.valueChanges.pipe(
            startWith(this.keyword.value),
            debounceTime(CreateUpdateRequestDialogComponent.SEARCH_DEBOUNCE_MS),
            distinctUntilChanged(),
            switchMap(keyword => this.lookupEmployees(keyword)),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(users => this.candidates.set(users));
    }

    /* 
     * Active accounts only: an inactive one cannot act on a request
     * The caller never appears: one extra row is fetched so the list still shows a full page
     * after they are dropped. The server refuses a self-request anyway.
     */
    private lookupEmployees(keyword: string): Observable<UserResponse[]> {
        const selfId = this.auth.user()?.id ?? null;
        
        // defer: the flag flips when the search starts, after switchMap dropped the previous one
        return defer(() => {
            this.searching.set(true);
            return this.userService.search({
                keyword: keyword.trim() || undefined,
                status: AccountStatus.Active,
                page: 0,
                size: CreateUpdateRequestDialogComponent.EMPLOYEE_LOOKUP_SIZE + 1,
            });
        }).pipe(
            map(page => page.content
                .filter(user => user.id !== selfId)
                .slice(0, CreateUpdateRequestDialogComponent.EMPLOYEE_LOOKUP_SIZE)
            ),
            catchError(() => of([] as UserResponse[])),
            finalize(() => this.searching.set(false)),
        );
    }

    private selectEmployee(user: UserResponse, preferredProfileId: string | null): void {
        this.employee.set(user);
        this.profiles.set([]);
        this.selectProfile(null);
        this.profilesLoading.set(true);

        this.profileService.listByEmployee({
            employeeId: user.id,
            page: 0,
            size: CreateUpdateRequestDialogComponent.PROFILE_LOOKUP_SIZE,
        }).subscribe({
            next: page => {
                this.profiles.set(page.content);
                this.profilesLoading.set(false);
                // Primary profile by default; no profile at all means "create one"
                const chosen = page.content.find(profile => profile.id === preferredProfileId)
                    ?? page.content.find(profile => profile.primary)
                    ?? null;
                this.selectProfile(chosen?.id ?? null);
            },
            error: () => this.profilesLoading.set(false),
        });
    }

    private selectProfile(profileId: string | null): void {
        this.selectedProfileId.set(profileId);
        this.profileCvs.set([]);
        if (!profileId) {
            return;
        }
        this.cvService.listByProfile(profileId).subscribe({
            // A late answer for a profile that is no longer selected is dropped
            next: cvs => {
                if (this.selectedProfileId() === profileId) {
                    this.profileCvs.set(cvs);
                }
            },
        });
    }

    private switchTargetCv(cvId: string | null): void {
        this.notes.set([]);
        this.notePickerOpen.set(false);
        this.cvContent.set(null);
        if (!cvId) {
            return;
        }

        if (this.contentCache.has(cvId)) {
            this.cvContent.set(this.contentCache.get(cvId) ?? null);
            return;
        }

        this.contentLoading.set(true);
        this.cvService.getById(cvId).subscribe({
            next: detail => {
                // Published content only: that is what the server checks anchors against
                this.contentCache.set(cvId, detail.content);
                if (this.targetCvId() === cvId) {
                    this.cvContent.set(detail.content);
                }
                this.contentLoading.set(false);
            },
            error: () => this.contentLoading.set(false),
        });
    }

    private resultMessage(result: CreateUpdateRequestResponse): string {
        const sent = result.created.length;
        const base = sent === 1 ? 'Update request sent' : `${sent} update requests sent`;
        if (!result.skipped.length) {
            return base;
        }
        const skipped = result.skipped.map(item => item.language).join(', ');
        return `${base}. Skipped ${skipped}: a request is already pending`;
    }
}