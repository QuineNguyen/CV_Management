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

// "All languages" on the create form; the server turns it into one request per language
export enum LanguageScope {
    All = 'ALL',
}

// Language sent when creating a request: one CV language or all of them
export type UpdateRequestLanguage = CvLanguage | LanguageScope;

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

export const LANGUAGE_SCOPE_LABELS: Record<LanguageScope, string> = {
    [LanguageScope.All]: 'All languages',
};