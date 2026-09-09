import { CvLanguage } from './cv-language.enum';

export enum ProficiencyLevel {
    Basic = 'BASIC',
    Elementary = 'ELEMENTARY',
    Intermediate = 'INTERMEDIATE',
    Advanced = 'ADVANCED',
    Expert = 'EXPERT',
}

export enum LanguageProficiency {
    Basic = 'BASIC',
    Conversational = 'CONVERSATIONAL',
    Professional = 'PROFESSIONAL',
    Fluent = 'FLUENT',
    Native = 'NATIVE',
}

export const PROFICIENCY_LEVEL_ORDER: readonly ProficiencyLevel[] = [
    ProficiencyLevel.Basic,
    ProficiencyLevel.Elementary,
    ProficiencyLevel.Intermediate,
    ProficiencyLevel.Advanced,
    ProficiencyLevel.Expert,
];

export const LANGUAGE_PROFICIENCY_ORDER: readonly LanguageProficiency[] = [
    LanguageProficiency.Basic,
    LanguageProficiency.Conversational,
    LanguageProficiency.Professional,
    LanguageProficiency.Fluent,
    LanguageProficiency.Native,
];

export const PROFICIENCY_LEVEL_LABELS: 
    Record<CvLanguage, Record<ProficiencyLevel, string>> = {
    [CvLanguage.Vi]: {
        [ProficiencyLevel.Basic]: 'Cơ bản',
        [ProficiencyLevel.Elementary]: 'Sơ cấp',
        [ProficiencyLevel.Intermediate]: 'Trung cấp',
        [ProficiencyLevel.Advanced]: 'Nâng cao',
        [ProficiencyLevel.Expert]: 'Chuyên gia',
    },
    [CvLanguage.En]: {
        [ProficiencyLevel.Basic]: 'Basic',
        [ProficiencyLevel.Elementary]: 'Elementary',
        [ProficiencyLevel.Intermediate]: 'Intermediate',
        [ProficiencyLevel.Advanced]: 'Advanced',
        [ProficiencyLevel.Expert]: 'Expert',
    },
    [CvLanguage.Ja]: {
        [ProficiencyLevel.Basic]: '基礎',
        [ProficiencyLevel.Elementary]: '初級',
        [ProficiencyLevel.Intermediate]: '中級',
        [ProficiencyLevel.Advanced]: '上級',
        [ProficiencyLevel.Expert]: 'エキスパート',
    },
};

export const LANGUAGE_PROFICIENCY_LABELS:
    Record<CvLanguage, Record<LanguageProficiency, string>> = {
    [CvLanguage.Vi]: {
        [LanguageProficiency.Basic]: 'Cơ bản',
        [LanguageProficiency.Conversational]: 'Giao tiếp',
        [LanguageProficiency.Professional]: 'Chuyên nghiệp',
        [LanguageProficiency.Fluent]: 'Lưu loát',
        [LanguageProficiency.Native]: 'Bản ngữ',
    },
    [CvLanguage.En]: {
        [LanguageProficiency.Basic]: 'Basic',
        [LanguageProficiency.Conversational]: 'Conversational',
        [LanguageProficiency.Professional]: 'Professional',
        [LanguageProficiency.Fluent]: 'Fluent',
        [LanguageProficiency.Native]: 'Native',
    },
    [CvLanguage.Ja]: {
        [LanguageProficiency.Basic]: '基礎',
        [LanguageProficiency.Conversational]: '日常会話',
        [LanguageProficiency.Professional]: 'ビジネス',
        [LanguageProficiency.Fluent]: '流暢',
        [LanguageProficiency.Native]: 'ネイティブ',
    },
};

export const NOT_SPECIFIED_LABELS: Record<CvLanguage, string> = {
    [CvLanguage.Vi]: 'Không xác định',
    [CvLanguage.En]: 'Not specified',
    [CvLanguage.Ja]: '未指定',
}