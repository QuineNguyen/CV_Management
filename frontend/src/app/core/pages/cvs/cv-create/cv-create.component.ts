import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal, viewChild } from "@angular/core";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CvContentEditorComponent } from "../cv-content-editor/cv-content-editor.component";
import { CvService } from "../../../services/cv.service";
import { CvProfileService } from "../../../services/cv-profile.service";
import { AuthService } from "../../../services/auth.service";
import { ActivatedRoute, Router } from "@angular/router";
import { ToastService } from "../../../services/toast.service";
import { CV_LANGUAGE_LABELS, CV_LANGUAGE_ORDER, CvLanguage } from "../../../enums/cv-language.enum";
import { CvProfileResponse } from "../../../dtos/cv-profile.dto";
import { CvResponse } from "../../../dtos/cv.dto";
import { LifecycleStatus } from "../../../enums/lifecycle-status.enum";
import { UserRole } from "../../../enums/user-role.enum";
import { CvContent, emptyCvContent } from "../../../models/cv-content.model";
import { QueryParam } from "../../../enums/query-param.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { HasUnsavedChanges } from "../../../services/unsaved-changes.guard";

/*
 * Three-step create flow: pick the profile, pick the language, fill in the content.
 *
 * - Only the owner writes CV content, so this page always works on the signed-in user's own
 * profiles - there is no employee paramater to honour.
 * 
 * - Languages already held by an ACTIVE CV of the chosen profile are hidden rather than shown
 * disabled: a deleted CV does not hold its slot, so "taken" here means genuinely unavailable.
 */
@Component({
    selector: 'app-cv-create',
    standalone: true,
    imports: [MatTooltipModule, CvContentEditorComponent],
    templateUrl: './cv-create.component.html',
    styleUrl: './cv-create.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvCreateComponent implements OnInit, HasUnsavedChanges {

    private readonly cvService = inject(CvService);
    private readonly profileService = inject(CvProfileService);
    private readonly auth = inject(AuthService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly toast = inject(ToastService);

    private readonly editor = viewChild(CvContentEditorComponent);

    readonly languageLabels = CV_LANGUAGE_LABELS;

    readonly step = signal(1);
    readonly loading = signal(true);
    readonly saving = signal(false);

    readonly profiles = signal<CvProfileResponse[]>([]);
    readonly selectedProfileId = signal<string | null>(null);

    readonly existingCvs = signal<CvResponse[]>([]);
    readonly languagesLoading = signal(false);
    readonly selectedLanguage = signal<CvLanguage | null>(null);

    /*
     * Language asked for by the caller, if any. Applied only once the profile's occupied slots are
     * known: the link may be stale and jumping to the content step for a language that has since
     * been taken would fail at save rather than at the point of choice.
     */
    private requestedLanguage: CvLanguage | null = null;

    readonly selectedProfile = computed(() =>
        this.profiles().find(profile => profile.id === this.selectedProfileId()) ?? null);

    // Only languages no active CV of this profile holds.
    readonly availableLanguages = computed(() => {
        const taken = new Set(this.existingCvs()
            .filter(cv => cv.lifecycleStatus === LifecycleStatus.Active)
            .map(cv => cv.language));

        return CV_LANGUAGE_ORDER.filter(language => !taken.has(language));
    });

    /*
     * Admin and HR owners publish v1 the moment they save; everyone else gets a draft that still
     * needs both approval levels. The wording of the final button has to say which.
     */
    readonly directPublish = computed(() => this.auth.hasRole(UserRole.Admin, UserRole.HR));

    // Shown pre-filled so the seeding is visible; the server re-applies it from the account.
    readonly seedContent = signal<CvContent>(emptyCvContent());

    ngOnInit(): void {
        const params = this.route.snapshot.queryParamMap;
        this.requestedLanguage = this.parseLanguage(params.get(QueryParam.Language));
        this.seedFromAccount();
        this.loadProfiles(params.get(QueryParam.ProfileId));
    }

    // ---------- Step 1: Profile ----------

    private loadProfiles(preselected: string | null): void {
        const employeeId = this.auth.user()?.id;
        if (!employeeId) {
            return;
        }

        this.profileService.listByEmployee({ employeeId, page: 0, size: 100 }).subscribe({
            next: result => {
                this.profiles.set(result.content);
                this.loading.set(false);

                if (!result.content.length) {
                    debugger;
                    this.createFirstProfile(employeeId);
                    return;
                }

                // The primary profile is the sensible default for someone with one persona.
                const chosen = result.content.find(profile => profile.id === preselected)
                    ?? result.content.find(profile => profile.primary)
                    ?? result.content[0];

                this.selectProfile(chosen.id);
            },
            error: () => this.loading.set(false),
        });
    }

    // An employee creating their first CV should not have to name a persona first.
    private createFirstProfile(employeeId: string): void {
        this.profileService.ensureProfile(employeeId).subscribe({
            next: profile => {
                this.profiles.set([profile]);
                this.selectProfile(profile.id);
            },
        });
    }

    selectProfile(profileId: string): void {
        this.selectedProfileId.set(profileId);
        this.selectedLanguage.set(null);
        this.loadExistingCvs(profileId);
    }

    // ---------- Step 2: Language ----------

    private loadExistingCvs(profileId: string): void {
        this.languagesLoading.set(true);

        this.cvService.listByProfile(profileId).subscribe({
            next: cvs => {
                this.existingCvs.set(cvs);
                this.languagesLoading.set(false);
                this.applyRequestedLanguage();
            },
            error: () => this.languagesLoading.set(false),
        });
    }

    /*
     * Skips ahead when the caller already named both the profile and a language that is still
     * free - asking someone to re-pick what they just clicked is a step that only looks careful.
     * Consumed one, so a Back is not immediately undone by this running again.
     */
    private applyRequestedLanguage(): void {
        const requested = this.requestedLanguage;
        this.requestedLanguage = null;

        if (!requested || !this.availableLanguages().includes(requested)) {
            return;
        }
        this.selectedLanguage.set(requested);
        this.step.set(3);
    }

    selectLanguage(language: CvLanguage): void {
        this.selectedLanguage.set(language);
    }

    // ---------- Navigation ----------

    canGoToStep(step: number): boolean {
        if (step === 1) {
            return true;
        }
        if (step === 2) {
            return !!this.selectedProfileId();
        }
        if (step === 3) {
            return !!this.selectedProfileId() && !!this.selectedLanguage();
        }
        return false;
    }

    goToStep(step: number): void {
        if (step === 2 && !this.selectedProfileId()) {
            return;
        }
        if (step === 3 && !this.selectedLanguage()) {
            return;
        }
        this.step.set(step);
    }

    next(): void {
        this.goToStep(this.step() + 1);
    }

    back(): void {
        this.step.update(current => Math.max(1, current - 1));
    }

    cancel(): void {
        void this.router.navigate(['/' + AppRoute.Profiles]);
    }

    // ---------- Save ----------

    save(): void {
        const profileId = this.selectedProfileId();
        const language = this.selectedLanguage();
        const editor = this.editor();

        if (!profileId || !language || !editor || this.saving()) {
            return;
        }
        if (!editor.valid) {
            editor.markAllTouched();
            this.toast.error('Fill in the required fields before saving');
            return;
        }

        this.saving.set(true);

        this.cvService.create(profileId, { language, content: editor.toContent() }).subscribe({
            next: created => {
                this.toast.success(this.directPublish()
                    ? `${this.languageLabels[language]} CV created and published as v1`
                    : `${this.languageLabels[language]} CV created as a draft`);

                void this.router.navigate(['/' + AppRoute.Cvs, created.id]);
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Helpers ----------

    languageLabel(language: CvLanguage): string {
        return this.languageLabels[language];
    }

    private seedFromAccount(): void {
        const user = this.auth.user();
        if (!user) {
            return;
        }
        this.seedContent.set({
            ...emptyCvContent(),
            personal_info: {
                full_name: user.fullName,
                date_of_birth: null,
                email: user.email,
                phone: null,
                address: null,
                position: null,
            },
        });
    }

    // Anything not one of the three known codes is treated as absent, not as an error.
    private parseLanguage(raw: string | null): CvLanguage | null {
        return CV_LANGUAGE_ORDER.find(language => language === raw) ?? null;
    }

    /*
     * Only the content step holds work worth protecting - abandoning a profile or language pick
     * costs a click and prompting for it would train people to dismiss the prompt.
     */
    hasUnsavedChanges(): boolean {
        return this.step() === 3 && !this.saving() && !!this.editor()?.dirty;
    }
}