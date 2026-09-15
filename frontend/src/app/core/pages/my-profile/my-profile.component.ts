import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal } from "@angular/core";
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from "@angular/forms";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { AvatarUploadComponent } from "../avatar-upload/avatar-upload.component";
import { MyProfileService } from "../../services/my-profile.service";
import { ToastService } from "../../services/toast.service";
import { ROLE_LABELS } from "../../models/user.model";
import { OPEN_PROFILE_UPDATE_STATUSES, PROFILE_UPDATE_STATUS_LABELS, ProfileUpdateStatus } from "../../enums/profile-update-status.enum";
import { MyProfileResponse, ProfileUpdateSubmitRequest } from "../../dtos/profile-update-request.dto";
import { AvatarChange } from "../../models/avatar-change.model";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../utils/app-date-adapter.util";
import { AuthService } from "../../services/auth.service";

/*
 * The user's own record and the one write they can perform on it.
 * 
 * Saving does two different things depending on who is asking and the server decides which:
 * an Admin's values land immediately, everyone else opens a request an Admin or HR reviews. The
 * page reads requiresApproval to word its buttons, never to pick a code path - there is only one.
 * 
 * Role, department, teams, email and username are shown read-only. They are not editable here by
 * design: each carries an organizational or access consequence and the request table has no
 * column to hold them.
 */
@Component({
    selector: 'app-my-profile',
    standalone: true,
    imports: [ReactiveFormsModule, DatePipe, AvatarUploadComponent, MatDatepickerModule, MatNativeDateModule],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './my-profile.component.html',
    styleUrl: './my-profile.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyProfileComponent implements OnInit {

    private static readonly CLOSE_ANIMATION_MS = 500;

    private readonly myProfileService = inject(MyProfileService);
    private readonly toast = inject(ToastService);
    private readonly auth = inject(AuthService);

    readonly roleLabels = ROLE_LABELS;
    readonly statusLabels = PROFILE_UPDATE_STATUS_LABELS;
    readonly status = ProfileUpdateStatus;

    readonly profile = signal<MyProfileResponse | null>(null);
    readonly loading = signal(true);
    readonly editing = signal(false);
    readonly saving = signal(false);

    readonly withdrawOpen = signal(false);
    readonly withdrawing = signal(false);
    readonly isWithdrawClosing = signal(false);
    private withdrawBackdropMouseDownTarget: EventTarget | null = null;

    // Avatar is edited outside the form: it is uploaded before saving, not typed into a field.
    readonly avatarImageId = signal<string | null>(null);
    readonly avatarUrl = signal<string | null>(null);

    readonly form = new FormGroup({
        fullName: new FormControl('', {
            nonNullable: true,
            validators: [Validators.required, Validators.maxLength(200)],
        }),
        dateOfBirth: new FormControl<Date | null>(null),
        phoneNumber: new FormControl<string | null>(null, [Validators.maxLength(30)]),
        address: new FormControl<string | null>(null, [Validators.maxLength(500)]),
    });

    readonly latestRequest = computed(() => this.profile()?.latestUpdateRequest ?? null);

    readonly hasPendingRequest = computed(() => {
        const status = this.latestRequest()?.status;
        return !!status && OPEN_PROFILE_UPDATE_STATUSES.includes(status);
    });

    /*
     * One open request at a time, so the form is closed while one is in flight. Letting someone
     * type a second proposal they cannot is worse than not offering the button.
     */
    readonly canEdit = computed(() => !this.hasPendingRequest());

    readonly requiresApproval = computed(() => this.profile()?.requiresApproval ?? true);

    readonly saveLabel = computed(() => {
        if (this.saving()) {
            return this.requiresApproval() ? 'Submitting...' : 'Saving...';
        }
        return this.requiresApproval() ? 'Submit for review' : 'Save changes';
    });

    ngOnInit(): void {
        this.load(true);
    }

    // ---------- Loading ----------

    private load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        this.myProfileService.getMyProfile().subscribe({
            next: profile => {
                this.profile.set(profile);
                this.resetForm(profile);
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    private resetForm(profile: MyProfileResponse): void {
        this.form.reset({
            fullName: profile.fullName,
            dateOfBirth: this.parseIsoDate(profile.dateOfBirth),
            phoneNumber: profile.phoneNumber,
            address: profile.address,
        });
        this.avatarImageId.set(profile.avatarImageId);
        this.avatarUrl.set(profile.avatarUrl);
    }

    // ---------- Editing ----------

    startEditing(): void {
        const profile = this.profile();
        if (!profile || !this.canEdit()) {
            return;
        }
        this.resetForm(profile);
        this.editing.set(true);
    }

    cancelEditing(): void {
        const profile = this.profile();
        if (profile) {
            this.resetForm(profile);
        }
        this.editing.set(false);
    }

    onAvatarChanged(change: AvatarChange): void {
        // Held locally until save: the image row exists, but nothing points at it yet.
        this.avatarImageId.set(change.imageId);
        this.avatarUrl.set(change.presignedUrl);
    }

    save(): void {
        const profile = this.profile();
        if (!profile || this.saving()) {
            return;
        }
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            this.toast.error('Fill in the required fields before saving');
            return;
        }

        const body = this.buildRequest(profile);
        if (!body) {
            this.toast.info('Nothing has changed yet');
            return;
        }

        this.saving.set(true);
        this.myProfileService.save(body).subscribe({
            next: updated => {
                this.profile.set(updated);
                this.resetForm(updated);
                this.editing.set(false);
                this.saving.set(false);
                this.syncSessionAvatar(updated);
                this.toast.success(updated.requiresApproval
                    ? 'Your request has been sent for review'
                    : 'Your profile has been updated'
                );
            },
            error: () => this.saving.set(false),
        });
    }

    /*
     * Keeps the toolbar and the CV create screen in step with what was actually applied.
     *
     * Reads the values back off the response rather than off the form: on the approval path the
     * server stored a request and changed nothing, so the profile that comes back still holds the
     * old photo - which is exactly what the session should keep showing.
     */
    private syncSessionAvatar(profile: MyProfileResponse): void {
        this.auth.patchUser({
            fullName: profile.fullName,
            avatarImageId: profile.avatarImageId,
            avatarUrl: profile.avatarUrl,
        });
    }

    /*
     * Sends only what actually differs. The server refuses a proposal that changes nothing, so
     * catching it here saves a round trip - and an unchanged field sent as a value would show up
     * on the reviewer's screen as a change they have to read past.
     */
    private buildRequest(profile: MyProfileResponse): ProfileUpdateSubmitRequest | null {
        const raw = this.form.getRawValue();
        const body: ProfileUpdateSubmitRequest = {};

        const fullName = raw.fullName.trim();
        if (fullName !== profile.fullName) {
            body.fullName = fullName;
        }
        if (this.formatIsoDate(raw.dateOfBirth) !== profile.dateOfBirth) {
            body.dateOfBirth = this.formatIsoDate(raw.dateOfBirth);
        }
        if (this.blankToNull(raw.phoneNumber) !== profile.phoneNumber) {
            body.phoneNumber = this.blankToNull(raw.phoneNumber);
        }
        if (this.blankToNull(raw.address) !== profile.address) {
            body.address = this.blankToNull(raw.address);
        }
        if (this.avatarImageId() !== profile.avatarImageId) {
            body.avatarImageId = this.avatarImageId();
        }

        return Object.keys(body).length ? body : null;
    }

    private blankToNull(value: string | null): string | null {
        const trimmed = value?.trim();
        return trimmed ? trimmed : null;
    }

    // ---------- Withdraw ----------
    
    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.withdrawOpen()) {
            this.cancelWithdraw();
        }
    }

    askWithdraw(): void {
        this.isWithdrawClosing.set(false);
        this.withdrawOpen.set(true);
    }

    onWithdrawBackdropMouseDown(event: MouseEvent): void {
        this.withdrawBackdropMouseDownTarget = event.target;
    }

    onWithdrawBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget
            && this.withdrawBackdropMouseDownTarget === event.currentTarget) {
                this.cancelWithdraw();
            }
        this.withdrawBackdropMouseDownTarget = null;
    }

    cancelWithdraw(): void {
        if (this.isWithdrawClosing() || this.withdrawing()) {
            return;
        }
        this.isWithdrawClosing.set(true);
        setTimeout(() => this.closeWithdraw(), MyProfileComponent.CLOSE_ANIMATION_MS);
    }

    confirmWithdraw(): void {
        this.withdrawing.set(true);
        this.myProfileService.withdrawPendingRequest().subscribe({
            next: () => {
                this.closeWithdraw();
                this.toast.success('Request withdrawn');
                this.load(false);
            },
            /*
             * A 409 here means a reviewer decided it in the meantime. The interceptor says so;
             * reloading is what lets the user see the outcome instead of pressing the button
             * again and getting the same error.
             */
            error: () => {
                this.closeWithdraw();
                this.load(false);
            }
        })
    }

    private closeWithdraw(): void {
        this.withdrawOpen.set(false);
        this.withdrawing.set(false);
        this.isWithdrawClosing.set(false);
    }

    // Parses an ISO date string (YYYY-MM-DD) into a local Date, or returns null.
    private parseIsoDate(iso: string | null | undefined): Date | null {
        if (!iso) {
            return null;
        }
        const [y, m, d] = iso.split('-').map(Number);
        return new Date(y, m - 1, d);
    }

    // Formats a Date into an ISO date string (YYYY-MM-DD), or returns null.
    private formatIsoDate(date: Date | null): string | null {
        if (!date || !(date instanceof Date) || isNaN(date.getTime())) {
            return null;
        }
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }
}