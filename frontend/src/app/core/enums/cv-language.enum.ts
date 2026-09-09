export enum CvLanguage {
    Vi = 'VI',
    En = 'EN',
    Ja = 'JA',
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