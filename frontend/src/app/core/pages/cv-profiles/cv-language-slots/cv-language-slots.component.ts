import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from "@angular/core";
import { CvResponse } from "../../../dtos/cv.dto";
import { CV_LANGUAGE_LABELS, CV_LANGUAGE_ORDER, CvLanguage } from "../../../enums/cv-language.enum";
import { Router, RouterLink } from "@angular/router";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CvService } from "../../../services/cv.service";
import { DRAFT_STATUS_LABELS } from "../../../enums/draft-status.enum";
import { AppRoute } from "../../../enums/app-route.enum";
import { LifecycleStatus } from "../../../enums/lifecycle-status.enum";
import { QueryParam } from "../../../enums/query-param.enum";

interface LanguageSlot {
    language: CvLanguage;
    cv: CvResponse | null;
}

/*
 * The three language slots of one profile: which are filled and a way into the empty ones.
 *
 * - Rendering all three rather than only the existing CVs is the point - "this profile has no
 * English CV yet" is the fact the page is there to convey and a list of what exists cannot say it.
 */
@Component({
    selector: 'app-cv-language-slots',
    standalone: true,
    imports: [RouterLink, MatTooltipModule],
    templateUrl: './cv-language-slots.component.html',
    styleUrl: './cv-language-slots.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvLanguageSlotsComponent implements OnInit {

    private readonly cvService = inject(CvService);
    private readonly router = inject(Router);

    readonly profileId = input.required<string>();
    // Creating a CV is the owner's own action; other viewers see the slots read-only.
    readonly canCreate = input(false);

    readonly languageLabels = CV_LANGUAGE_LABELS;
    readonly draftStatusLabels = DRAFT_STATUS_LABELS;
    readonly cvsRoute = '/' + AppRoute.Cvs;

    readonly slots = signal<LanguageSlot[]>([]);
    readonly loading = signal(true);

    ngOnInit(): void {
        this.cvService.listByProfile(this.profileId()).subscribe({
            next: cvs => {
                const active = cvs.filter(cv => cv.lifecycleStatus === LifecycleStatus.Active);

                this.slots.set(CV_LANGUAGE_ORDER.map(language => ({
                    language,
                    cv: active.find(cv => cv.language === language) ?? null,
                })));
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    create(language: CvLanguage): void {
        void this.router.navigate(['/' + AppRoute.CvsNew], {
            queryParams: { [QueryParam.ProfileId]: this.profileId(), language },
        });
    }
}