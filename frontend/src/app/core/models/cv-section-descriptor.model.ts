import { CvLanguage } from "../enums/cv-language.enum";
import { CvSectionKey } from "../enums/cv-section-key.enum";
import { LANGUAGE_PROFICIENCY_LABELS, LANGUAGE_PROFICIENCY_ORDER, NOT_SPECIFIED_LABELS } from "../enums/proficiency.enum";
import { PROFICIENCY_LEVEL_LABELS, PROFICIENCY_LEVEL_ORDER, ProficiencyLevel } from "../enums/proficiency.enum";
import { CvDateStyle } from "../utils/cv-date.util";

export enum FieldKind {
    Text = 'TEXT',
    Textarea = 'TEXTAREA',
    Date = 'DATE',
    Number = 'NUMBER',
    Select = 'SELECT',
    Tags = 'TAGS',
}

export enum OptionSet {
    Proficiency = 'PROFICIENCY',
    LanguageProficiency = 'LANGUAGE_PROFICIENCY',
}

export interface SelectOption {
    value: string;
    label: string;
}

export interface FieldDescriptor {
    // The JSON key, used verbatim as the form control name.
    key: string;
    label: string;
    kind: FieldKind;
    required?: boolean;
    maxLength?: number;
    placeholder?: string;
    hint?: string;
    optionSet?: OptionSet;
    // For Date fields: how the preview renders. Ranges read as month and year on a CV.
    dateStyle?: CvDateStyle;
    // Half-width on desktop; full width below the iPad breakpoint regardless.
    half?: boolean;
}

export interface SectionDescriptor {
    key: CvSectionKey;
    label: string;
    description: string;
    repeated: boolean;
    // Empty-state and add-button wording.
    itemNoun?: string;
    // Which field titles a collapsed item row.
    titleField?: string;
    subtitleField?: string;
    fields: readonly FieldDescriptor[];
}

export function optionsFor(optionSet: OptionSet, language: CvLanguage): readonly SelectOption[] {
    if (optionSet === OptionSet.Proficiency) {
        const labels = PROFICIENCY_LEVEL_LABELS[language];
        return PROFICIENCY_LEVEL_ORDER.map(value => ({ value, label: labels[value] }));
    }
    const labels = LANGUAGE_PROFICIENCY_LABELS[language];
    return LANGUAGE_PROFICIENCY_ORDER.map(value => ( { value, label: labels[value] }));
}

export function notSpecifiedLabel(language: CvLanguage): string {
    return NOT_SPECIFIED_LABELS[language];
}

/*
 * The 9 fixed sections of the standard template, in display order.
 * 
 * - Personal info, skills and experience are what the backend checks before a draft may be
 * submitted, which is why their required flags matter beyond cosmetics.
 */
export const CV_SECTIONS: readonly SectionDescriptor[] = [
    {
        key: CvSectionKey.PersonalInfo,
        label: 'Personal information',
        description: 'Copied from your account when the CV was created, then independent of it',
        repeated: false,
        fields: [
            { key: 'full_name', label: 'Full name', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'position', label: 'Job title', kind: FieldKind.Text, maxLength: 255, half: true, placeholder: 'e.g. Backend Developer' },
            { key: 'email', label: 'Email', kind: FieldKind.Text, maxLength: 255, half: true },
            { key: 'phone', label: 'Phone', kind: FieldKind.Text, maxLength: 50, half: true },
            { key: 'date_of_birth', label: 'Date of birth', kind: FieldKind.Date, dateStyle: 'full', half: true },
            { key: 'address', label: 'Address', kind: FieldKind.Text, maxLength: 500, half: true },
        ],
    },
    {
        key: CvSectionKey.CareerObjective,
        label: 'Career objective',
        description: 'A short statement of what you are aiming for',
        repeated: false,
        fields: [
            { key: 'content', label: 'Objective', kind: FieldKind.Textarea, maxLength: 2000 },
        ],
    },
    {
        key: CvSectionKey.Skills,
        label: 'Skills',
        description: 'Required before a CV can be submitted for approval',
        repeated: true,
        itemNoun: 'skill',
        titleField: 'skill_name',
        subtitleField: 'proficiency',
        fields: [
            { key: 'skill_name', label: 'Skill', kind: FieldKind.Text, required: true, maxLength: 255, half: true, placeholder: 'e.g. Spring Boot' },
            { key: 'proficiency', label: 'Proficiency', kind: FieldKind.Select, optionSet: OptionSet.Proficiency, half: true },
            { key: 'note', label: 'Note', kind: FieldKind.Text, maxLength: 500, placeholder: 'Years of use, context...' },
        ],
    },
    {
        key: CvSectionKey.Experience,
        label: 'Work experience',
        description: 'Required before a CV can be submitted for approval',
        repeated: true,
        itemNoun: 'position',
        titleField: 'position',
        subtitleField: 'company',
        fields: [
            { key: 'company', label: 'Company', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'position', label: 'Position', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'start_date', label: 'From', kind: FieldKind.Date, dateStyle: 'monthYear', half: true },
            { key: 'end_date', label: 'To', kind: FieldKind.Date, dateStyle: 'monthYear', half: true, hint: 'Leave empty if this is your current role' },
            { key: 'description', label: 'What you did', kind: FieldKind.Textarea, maxLength: 4000 },
        ],
    },
    {
        key: CvSectionKey.Education,
        label: 'Education',
        description: 'Schools, universities and formal training',
        repeated: true,
        itemNoun: 'entry',
        titleField: 'institution',
        subtitleField: 'degree',
        fields: [
            { key: 'institution', label: 'Institution', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'degree', label: 'Degree', kind: FieldKind.Text, maxLength: 255, half: true },
            { key: 'field', label: 'Field of study', kind: FieldKind.Text, maxLength: 255, half: true },
            { key: 'start_date', label: 'From', kind: FieldKind.Date, dateStyle: 'monthYear', half: true },
            { key: 'end_date', label: 'To', kind: FieldKind.Date, dateStyle: 'monthYear', half: true, hint: 'Leave empty if still studying' },
            { key: 'description', label: 'Notes', kind: FieldKind.Textarea, maxLength: 2000 },
        ],
    },
    {
        key: CvSectionKey.Certifications,
        label: 'Certifications',
        description: 'Professional certificates and their validity',
        repeated: true,
        itemNoun: 'certificate',
        titleField: 'name',
        subtitleField: 'issuing_organization',
        fields: [
            { key: 'name', label: 'Certificate', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'issuing_organization', label: 'Issued by', kind: FieldKind.Text, maxLength: 255, half: true },
            { key: 'issue_date', label: 'Issued on', kind: FieldKind.Date, dateStyle: 'full', half: true },
            { key: 'expiry_date', label: 'Expires on', kind: FieldKind.Date, dateStyle: 'full', half: true, hint: 'Leave empty if it does not expire' },
            { key: 'credential_id', label: 'Credential ID', kind: FieldKind.Text, maxLength: 255 },
        ],
    },
    {
        key: CvSectionKey.Projects,
        label: 'Projects',
        description: 'What you built, with whom and using what',
        repeated: true,
        itemNoun: 'project',
        titleField: 'name',
        subtitleField: 'role',
        fields: [
            { key: 'name', label: 'Project', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'role', label: 'Your role', kind: FieldKind.Text, maxLength: 255, half: true },
            { key: 'team_size', label: 'Team size', kind: FieldKind.Number, half: true },
            { key: 'start_date', label: 'From', kind: FieldKind.Date, dateStyle: 'monthYear', half: true },
            { key: 'end_date', label: 'To', kind: FieldKind.Date, dateStyle: 'monthYear', half: true, hint: 'Leave empty if ongoing' },
            { key: 'technologies', label: 'Technologies', kind: FieldKind.Tags, placeholder: 'Java, Angular, MariaDB', hint: 'Separate with commas' },
            { key: 'description', label: 'Description', kind: FieldKind.Textarea, maxLength: 4000 },
        ],
    },
    {
        key: CvSectionKey.Languages,
        label: 'Languages',
        description: 'Languages you speak - not the language this CV is written in',
        repeated: true,
        itemNoun: 'language',
        titleField: 'language_name',
        subtitleField: 'proficiency',
        fields: [
            { key: 'language_name', label: 'Language', kind: FieldKind.Text, required: true, maxLength: 255, half: true },
            { key: 'proficiency', label: 'Proficiency', kind: FieldKind.Select, optionSet: OptionSet.LanguageProficiency, half: true },
            { key: 'certification', label: 'Certificate', kind: FieldKind.Text, maxLength: 255, placeholder: 'e.g. JLPT N2' },
        ],
    },
    {
        key: CvSectionKey.AdditionalInfo,
        label: 'Additional information',
        description: 'Awards, publications, interests, references',
        repeated: true,
        itemNoun: 'entry',
        titleField: 'title',
        fields: [
            { key: 'title', label: 'Title', kind: FieldKind.Text, required: true, maxLength: 255 },
            { key: 'content', label: 'Content', kind: FieldKind.Textarea, maxLength: 4000 },
        ],
    },
];

export const REPEATED_SECTIONS = CV_SECTIONS.filter(section => section.repeated);
export const SINGLE_SECTIONS = CV_SECTIONS.filter(section => !section.repeated);