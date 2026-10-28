import { CvResponse } from "../dtos/cv.dto";
import { CV_LANGUAGE_ORDER, CV_SLOT_STATE_LABELS, CvLanguage, CvSlotState } from "../enums/cv-language.enum";
import { LifecycleStatus } from "../enums/lifecycle-status.enum";
import { pluralize } from "../utils/plural.util";

// One of the three language positions of a profile; CV and state are null when no CV exists.
export interface LanguageSlot {
    language: CvLanguage;
    cv: CvResponse | null;
    state: CvSlotState | null;
}

// A slot rendered as a tab on the CV detail page.
export interface CvLanguageTab extends LanguageSlot {
    active: boolean;
    // "v3", "Draft only" or "Empty"
    stateLabel: string | null;
    // "2 items untranslated"; null when nothing is waiting
    untranslatedLabel: string | null;
}

// A published CV stays Published while a new draft is open; the draft badge says the rest,
export function slotStateOf(cv: CvResponse): CvSlotState {
    if (cv.currentVersionNumber) {
        return CvSlotState.Published;
    }
    return cv.openDraftStatus ? CvSlotState.Drafting : CvSlotState.Empty;
}

// Only ACTIVE CVs hold a language slot; a deleted one frees it.
export function activeCvsOf(cvs: readonly CvResponse[]): CvResponse[] {
    return cvs.filter(cv => cv.lifecycleStatus === LifecycleStatus.Active);
}

// Languages the profile can still add; shared by the create wizard and the detail page.
export function freeLanguagesOf(cvs: readonly CvResponse[]): CvLanguage[] {
    const taken = new Set(activeCvsOf(cvs).map(cv => cv.language));
    return CV_LANGUAGE_ORDER.filter(language => !taken.has(language));
}

// The ACTIVE CV pointing at no master is the master.
export function activeMasterOf(cvs: readonly CvResponse[]): CvResponse | null {
    return activeCvsOf(cvs).find(cv => cv.master) ?? null;
}

// Tabs in template order; the CV on screen keeps its tab even if it is not ACTIVE.
export function languageTabsOf(cvs: readonly CvResponse[], current: CvResponse | null): CvLanguageTab[] {
    const active = activeCvsOf(cvs);

    return CV_LANGUAGE_ORDER.map(language => {
        const cv = active.find(item => item.language === language)
            ?? (current?.language === language ? current : null);
        const state = cv ? slotStateOf(cv) : null;

        return {
            language,
            cv,
            state,
            active: !!cv && cv.id === current?.id,
            stateLabel: cv && state ? stateLabelOf(cv, state) : null,
            untranslatedLabel: cv && cv.untranslatedItemCount > 0
                ? `${pluralize(cv.untranslatedItemCount, 'item')} untranslated`
                : null,
        };
    });
}

function stateLabelOf(cv: CvResponse, state: CvSlotState): string {
    return state === CvSlotState.Published ? `v${cv.currentVersionNumber}` : CV_SLOT_STATE_LABELS[state];
}