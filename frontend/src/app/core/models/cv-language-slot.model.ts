import { CvResponse } from "../dtos/cv.dto";
import { CvLanguage, CvSlotState } from "../enums/cv-language.enum";

// One of the three language positions of a profile; CV and state are null when no CV exists.
export interface LanguageSlot {
    language: CvLanguage;
    cv: CvResponse | null;
    state: CvSlotState | null;
}

// A published CV stays Published while a new draft is open; the draft badge says the rest,
export function slotStateOf(cv: CvResponse): CvSlotState {
    if (cv.currentVersionNumber) {
        return CvSlotState.Published;
    }
    return cv.openDraftStatus ? CvSlotState.Drafting : CvSlotState.Empty;
}