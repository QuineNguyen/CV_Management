import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, input, OnInit, output, signal } from "@angular/core";
import { UserService } from "../../../services/user.service";
import { DeactivateUserRequest, TeamReplacement, TechLeadOption, UserResponse } from "../../../dtos/user.dto";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

@Component({
    selector: 'app-user-deactivate-dialog',
    standalone: true,
    imports: [],
    templateUrl: './user-deactivate-dialog.component.html',
    styleUrl: './user-deactivate-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDeactivateDialogComponent implements OnInit {

    private readonly userService = inject(UserService);
    private readonly destroyRef = inject(DestroyRef);

    readonly user = input.required<UserResponse>();
    readonly submitting = input(false);

    readonly confirmed = output<DeactivateUserRequest>();
    readonly cancelled = output<void>();

    readonly isClosing = signal(false);

    readonly techLeads = signal<TechLeadOption[]>([]);

    // Replacement chosen per led team, keyed by team id
    readonly replacements = signal<Record<string, string>>({});

    readonly ledTeams = computed(() => this.user().ledTeams);
    readonly hasLedTeams = computed(() => this.ledTeams().length > 0);

    // Candidates exclude the user being deactivated, who cannot replace themselves
    readonly candidates = computed(() =>
        this.techLeads().filter(lead => lead.id !== this.user().id)
    );

    // Every led team must be handed over before the account can be closed
    readonly canSubmit = computed(() => {
        if (this.submitting()) {
            return false;
        }
        const chosen = this.replacements();
        return this.ledTeams().every(team => !!chosen[team.id]);
    });

    ngOnInit(): void {
        if (this.hasLedTeams()) {
            this.loadTechLeads();
        }
    }

    onReplacementChange(teamId: string, replacementId: string): void {
        this.replacements.update(current => ({ ...current, [teamId]: replacementId }));
    }

    replacementFor(teamId: string): string {
        return this.replacements()[teamId] ?? '';
    }

    onConfirm(): void {
        if (!this.canSubmit()) {
            return;
        }
        
        const chosen = this.replacements();
        const payload: TeamReplacement[] = this.ledTeams().map(team => ({
            teamId: team.id,
            replacementTechLeadId: chosen[team.id],
        }));

        this.confirmed.emit({ replacements: payload });
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.onCancel();
    }

    onCancel(): void {
        if (this.isClosing() || this.submitting()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => {
            this.cancelled.emit();
        }, 500);
    }

    private loadTechLeads(): void {
        this.userService.getTechLeads()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(leads => this.techLeads.set(leads));
    }
}