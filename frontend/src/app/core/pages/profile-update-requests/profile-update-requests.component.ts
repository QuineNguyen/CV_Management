import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule, Validators } from "@angular/forms";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ProfileUpdateRequestService } from "../../services/profile-update-request.service";
import { ToastService } from "../../services/toast.service";
import { PROFILE_UPDATE_STATUS_LABELS, ProfileUpdateStatus } from "../../enums/profile-update-status.enum";
import { ProfileUpdateRequestResponse } from "../../dtos/profile-update-request.dto";
import { UserPageState } from "../../models/user-page.model";
import { ProfileDiffRow } from "../../models/profile-dif-row.mocel";
import { ProfileUpdateSortField, SortDirection } from "../../enums/sort-field.enum";

/*
 * The reviewer queue for self-service profile changes.
 * 
 * Nothing here filters by role. The server already scoped every response by the requester's role
 * - an Admin receives every request, an HR receives only those from employees and tech leads -
 * so a row reaching this component is a row this viewer may decide on.
 * 
 * Approve and Reject are compare-and-set on the server. Losing that race is not an error to
 * apologise for: it means someone else answered first, so the list reloads and shows what they
 * decided rather than leaving the reviewer to press the button again.
 */
@Component({
    selector: 'app-profile-update-requests',
    standalone: true,
    imports: [ReactiveFormsModule, MatPaginatorModule, MatTooltipModule, DatePipe],
    templateUrl: './profile-update-requests.component.html',
    styleUrl: './profile-update-requests.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileUpdateRequestsComponent implements OnInit {

    private static readonly DEFAULT_PAGE_SIZE = 20;
    private static readonly CLOSE_ANIMATION_MS = 500;
    private static readonly UNCHANGED = 'unchanged';
    private static readonly EMPTY = '-';

    private readonly requestService = inject(ProfileUpdateRequestService);
    private readonly toast = inject(ToastService);

    readonly pageSizeOptions = [5, 10, 20, 50];
    readonly statusLabels = PROFILE_UPDATE_STATUS_LABELS;
    readonly status = ProfileUpdateStatus;
    readonly statusOptions = Object.values(ProfileUpdateStatus);

    readonly requests = signal<ProfileUpdateRequestResponse[]>([]);
    readonly loading = signal(false);

    // PENDING by default: the queue exists to be emptied, not browsed.
    readonly statusFilter = signal<ProfileUpdateStatus | null>(ProfileUpdateStatus.Pending);
    readonly statusOpen = signal(false);

    readonly pageState = signal<UserPageState>({
        index: 0,
        size: ProfileUpdateRequestsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly reviewTarget = signal<ProfileUpdateRequestResponse | null>(null);
    readonly isReviewClosing = signal(false);
    readonly deciding = signal(false);
    // The reject reason only appears once Reject is pressed; it is not a field of the review form.
    readonly rejecting = signal(false);
    private reviewBackdropMouseDownTarget: EventTarget | null = null;

    readonly rejectReason = new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(500)],
    });

    readonly isEmpty = computed(() => !this.loading() && this.requests().length === 0);

    readonly selectedStatusLabel = computed(() => {
        const status = this.statusFilter();
        return status ? this.statusLabels[status] : 'All statuses';
    });

    // Only a PENDING request can be decidesd; the dialog opens read-only for the other three.
    readonly canDecide = computed(() => this.reviewTarget()?.status === ProfileUpdateStatus.Pending);

    readonly diffRows = computed<ProfileDiffRow[]>(() => {
        const request = this.reviewTarget();
        return request ? this.buildDiff(request) : [];
    })

    ngOnInit(): void {
        this.load(true);
    }

    // ---------- Loading ----------
    
    load(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        const { index, size } = this.pageState();

        this.requestService.list({
            status: this.statusFilter() ?? undefined,
            sortBy: ProfileUpdateSortField.CreateAt,
            direction: SortDirection.Desc,
            page: index,
            size,
        }).subscribe({
            next: result => {
                this.requests.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.load(true);
    }

    toggleStatusDropdown(event: MouseEvent): void {
        event.stopPropagation();
        this.statusOpen.update(open => !open);
    }

    selectStatus(status: ProfileUpdateStatus | null): void {
        this.statusFilter.set(status);
        this.statusOpen.set(false);
        this.pageState.update(state => ({ ...state, index: 0 }));
        this.load(true);
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        if (this.statusOpen()) {
            this.statusOpen.set(false);
        }
    }

    openReview(request: ProfileUpdateRequestResponse): void {
        this.isReviewClosing.set(false);
        this.rejecting.set(false);
        this.rejectReason.reset('');
        this.reviewTarget.set(request);
    }

    onReviewBackdropMouseDown(event: MouseEvent): void {
        this.reviewBackdropMouseDownTarget = event.target;
    }

    onReviewBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget
            && this.reviewBackdropMouseDownTarget === event.currentTarget
        ) {
            this.closeReview();
        }
        this.reviewBackdropMouseDownTarget = null;
    }

    closeReview(): void {
        if (this.isReviewClosing() || this.deciding()) {
            return;
        }
        this.isReviewClosing.set(true);
        setTimeout(() => this.dismissReview(), ProfileUpdateRequestsComponent.CLOSE_ANIMATION_MS);
    }

    private dismissReview(): void {
        this.reviewTarget.set(null);
        this.isReviewClosing.set(false);
        this.rejecting.set(false);
        this.deciding.set(false);
    }

    // ---------- Decisions ----------

    approve(): void {
        const target = this.reviewTarget();
        if (!target || this.deciding()) {
            return;
        }
        this.deciding.set(true);

        this.requestService.approve(target.id).subscribe({
            next: decided => {
                this.dismissReview();
                this.toast.success(`Changes applied to ${decided.userFullName}`);
                this.load(false);
            },
            error: () => this.onDecisionFailed(),
        });
    }

    startRejecting(): void {
        this.rejecting.set(true);
    }

    cancelRejecting(): void {
        this.rejecting.set(false);
        this.rejectReason.reset('');
    }

    reject(): void {
        const target = this.reviewTarget();
        if (!target || this.deciding()) {
            return;
        }
        if (this.rejectReason.invalid) {
            this.rejectReason.markAsTouched();
            this.toast.error('A reason is required; the requester will see it');
            return;
        }
        this.deciding.set(true);

        this.requestService.reject(target.id, this.rejectReason.value.trim()).subscribe({
            next: decided => {
                this.dismissReview();
                this.toast.success(`Request from ${decided.userFullName} declined`);
                this.load(false);
            },
            error: () => this.onDecisionFailed(),
        })
    }

    /*
     * Covers both a lost compare-and-set and a request withdrawn mid-review. The interceptor has
     * already said which; closing and reloading is what lets the reviewer see the new state
     * instead of pressing the same button against a row that no longer accepts it.
     */
    private onDecisionFailed(): void {
        this.dismissReview();
        this.load(false);
        this.requestService.refreshPendingCount();
    }

    // ---------- Diff ----------

    /*
     * Every in-scope field appears, changed or not. Dropping the untouched ones would leave the
     * reviewer guessing whether a missing line means "same" or "cleared" - and this path cannot
     * clear anything, so the distinction matters.
     */
    private buildDiff(request: ProfileUpdateRequestResponse): ProfileDiffRow[] {
        return [
            this.row('Full name', request.currentFullName, request.requestedFullName),
            this.row('Date of birth', request.currentDateOfBirth, request.requestedDateOfBirth),
            this.row('Phone number', request.currentPhoneNumber, request.requestedPhoneNumber),
            this.row('Address', request.currentAddress, request.requestedAddress),
            {
                label: 'Photo',
                current: request.currentAvatarUrl ?? '',
                requested: request.requestedAvatarUrl ?? '',
                changed: request.requestedAvatarImageId !== null,
                isPhoto: true,
            }
        ]
    }

    private row(label: string, current: string | null, requested: string | null): ProfileDiffRow {
        return {
            label,
            current: current ?? ProfileUpdateRequestsComponent.EMPTY,
            requested: requested ?? ProfileUpdateRequestsComponent.UNCHANGED,
            changed: requested !== null,
            isPhoto: false,
        };
    }
}