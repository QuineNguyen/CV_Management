import { CvLanguage } from "./cv-language.enum";

/*
 * Title shown on an untranslated placeholder until the owner types one, in the CV's own language.
 * Display only, never saved: written into the content it would count as "filled in" (clearing the
 * untranslated flag on the first save) and could reach the published version and the PDF.
 * Inner keys are the section keys of the CV content JSON.
 */
export const SYNC_PLACEHOLDER_LABELS: Readonly<Record<CvLanguage, Readonly<Record<string, string>>>> = {
    VI: {
        skills: 'Kỹ năng mới - vui lòng dịch',
        experience: 'Kinh nghiệm mới - vui lòng dịch',
        education: 'Học vấn mới - vui lòng dịch',
        certifications: 'Chứng chỉ mới - vui lòng dịch',
        projects: 'Dự án mới - vui lòng dịch',
        languages: 'Ngoại ngữ mới - vui lòng dịch',
        additional_info: 'Mục mới - vui lòng dịch',
    },
    EN: {
        skills: 'New skill - please translate',
        experience: 'New experience - please translate',
        education: 'New education - please translate',
        certifications: 'New certification - please translate',
        projects: 'New project - please translate',
        languages: 'New language - please translate',
        additional_info: 'New entry - please translate',
    },
    JA: {
        skills: '新しいスキル — 翻訳してください',
        experience: '新しい職務経歴 — 翻訳してください',
        education: '新しい学歴 — 翻訳してください',
        certifications: '新しい資格 — 翻訳してください',
        projects: '新しいプロジェクト — 翻訳してください',
        languages: '新しい語学 — 翻訳してください',
        additional_info: '新しい項目 — 翻訳してください',
    },
};

// Null for a section with no label, so the caller falls back to its generic title
export function syncPlaceholderLabelOf(language: CvLanguage, sectionKey: string): string | null {
    return SYNC_PLACEHOLDER_LABELS[language]?.[sectionKey] ?? null;
}