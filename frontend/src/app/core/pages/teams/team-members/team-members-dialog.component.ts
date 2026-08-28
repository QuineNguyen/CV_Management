import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { MatTooltipModule } from "@angular/material/tooltip";
import { TeamService } from "../../../services/team.service";
import { UserService } from "../../../services/user.service";
import { ToastService } from "../../../services/toast.service";
import { TeamMemberResponse, TeamResponse } from "../../../dtos/team.dto";
import { ROLE_LABELS } from "../../../models/user.model";
import { UserResponse } from "../../../dtos/user.dto";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { catchError, debounceTime, distinctUntilChanged, EMPTY, switchMap, tap } from "rxjs";
import { AccountStatus } from "../../../enums/account-status.enum";

@Component({
    selector: 'app-team-members-dialog',
    standalone: true,
    imports: [ReactiveFormsModule, MatTooltipModule],
    templateUrl: './team-members-dialog.component.html',
    styleUrl: './team-members-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamMembersDialogComponent implements OnInit {

    private static readonly LOOKUP_SIZE = 10;
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    // One character matches almost everyone, which makes the list useless
    private static readonly MIN_KEYWORD_LENGTH = 2;

    private readonly teamService = inject(TeamService);
    private readonly userService = inject(UserService);
    private readonly toast = inject(ToastService);
    private readonly destroyRef = inject(DestroyRef);

    readonly team = input.required<TeamResponse>();

    // Emits whether membership changed, so the caller knows to reload its list.
    readonly closed = output<boolean>();

    readonly roleLabels = ROLE_LABELS;

    readonly isClosing = signal(false);
    readonly isRemoveClosing = signal(false);

    readonly members = signal<TeamMemberResponse[]>([]);
    readonly loading = signal(false);
    readonly busyUserId = signal<string | null>(null);

    readonly keyword = new FormControl('', { nonNullable: true });
    readonly candidates = signal<UserResponse[]>([]);
    readonly lookupLoading = signal(false);

    readonly removeTarget = signal<TeamMemberResponse | null>(null);

    readonly isEmpty = computed(() => !this.loading() && this.members().length === 0);

    private changed = false;

    ngOnInit(): void {
        this.loadMembers(true);
        this.watchKeyword();
    }

    // ---------- Members ----------

    loadMembers(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }

        this.teamService.getMembers(this.team().id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: members => {
                    this.members.set(members);
                    this.loading.set(false);
                },
                error: () => this.loading.set(false),
            });
    }

    addMember(user: UserResponse): void {
        this.busyUserId.set(user.id);

        this.teamService.addMember(this.team().id, user.id).subscribe({
            next: () => {
                this.busyUserId.set(null);
                this.changed = true;
                this.keyword.setValue('', { emitEvent: false });
                this.candidates.set([]);
                this.toast.success(`${user.fullName} added to ${this.team().code}`);
                this.loadMembers(false);
            },
            error: () => this.busyUserId.set(null),
        });
    }

    // ---------- Remove ----------
    
    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.removeTarget()) {
            this.cancelRemove();
            return;
        }
        this.onClose();
    }

    askRemove(member: TeamMemberResponse): void {
        this.isRemoveClosing.set(false);
        this.removeTarget.set(member);
    }

    cancelRemove(): void {
        if (this.isRemoveClosing() || (this.removeTarget() && this.busyUserId() === this.removeTarget()?.userId)) {
            return;
        }
        this.isRemoveClosing.set(true);
        setTimeout(() => {
            this.removeTarget.set(null);
            this.isRemoveClosing.set(false);
        }, 500);
    }

    confirmRemove(): void {
        const target = this.removeTarget();
        if (!target) {
            return;
        }

        this.busyUserId.set(target.userId);
        this.teamService.removeMember(this.team().id, target.userId).subscribe({
            next: () => {
                this.busyUserId.set(null);
                this.removeTarget.set(null);
                this.isRemoveClosing.set(false);
                this.changed = true;
                this.toast.success(`${target.fullName} removed from ${this.team().code}`);
                this.loadMembers(false);
            },
            error: () => {
                this.busyUserId.set(null);
                this.removeTarget.set(null);
                this.isRemoveClosing.set(false);
            },
        });
    }

    onClose(): void {
        if (this.isClosing()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => {
            this.closed.emit(this.changed);
        }, 500);
    }

    // ---------- Internals ----------

    private watchKeyword(): void {
        this.keyword.valueChanges.pipe(
            debounceTime(TeamMembersDialogComponent.SEARCH_DEBOUNCE_MS),
            distinctUntilChanged(),
            tap(value => {
                const tooShort = value.trim().length < TeamMembersDialogComponent.MIN_KEYWORD_LENGTH;
                if (tooShort) {
                    this.candidates.set([]);
                }
                this.lookupLoading.set(!tooShort);
            }),
            switchMap(value => {
                if (value.trim().length < TeamMembersDialogComponent.MIN_KEYWORD_LENGTH) {
                    return EMPTY;
                }
                return this.userService.search({
                    keyword: value.trim(),
                    status: AccountStatus.Active,
                    page: 0,
                    size: TeamMembersDialogComponent.LOOKUP_SIZE,
                }).pipe(
                    catchError(() => {
                        this.lookupLoading.set(false);
                        return EMPTY;
                    })
                )
            }),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(page => {
            // Existing members are filtered out so the list only offers real additions
            const memberIds = new Set(this.members().map(member => member.userId));
            this.candidates.set(page.content.filter(user => !memberIds.has(user.id)));
            this.lookupLoading.set(false);
        });
    }
}