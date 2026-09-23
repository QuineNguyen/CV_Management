export enum CvLanguage {
    Vi = 'VI',
    En = 'EN',
    Ja = 'JA',
}

// What a filled language slot holds, derived from its version and open draft.
export enum CvSlotState {
    Published = 'PUBLISHED',
    Drafting = 'DRAFTING',
    // Never published and nothing open: only reachable once the first draft is cancelled.
    Empty = 'EMPTY',
}

export const CV_LANGUAGE_LABELS: Record<CvLanguage, string> = {
    [CvLanguage.Vi]: 'Vietnamese',
    [CvLanguage.En]: 'English',
    [CvLanguage.Ja]: 'Japanese',
};

// Template order and the order the language picker offers them in.
export const CV_LANGUAGE_ORDER: readonly CvLanguage[] = [
    CvLanguage.Vi,
    CvLanguage.En,
    CvLanguage.Ja,
];