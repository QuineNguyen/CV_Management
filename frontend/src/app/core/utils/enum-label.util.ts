import { Pipe, PipeTransform } from "@angular/core";

// Words kept in capitals when a server enum name is turned into a label
const ACRONYMS: Readonly<Record<string, string>> = {
    CV: 'CV',
    CVS: 'CVs',
    HR: 'HR',
    SLA: 'SLA',
    PDF: 'PDF',
    ID: 'ID',
};

/*
 * Server enum name to sentence case, so new backend values get a readable label without a
 * client-side lookup table: 'UPDATE_SYSTEM_CONFIG' -> 'Update system config', 'CREATE_CV' -> 'Create CV'.
 */
export function labelOfEnum(value: string | null | undefined): string {
    if (!value) {
        return '';
    }
    return value
        .split('_')
        .filter(word => word.length > 0)
        .map((word, index) => {
            const acronym = ACRONYMS[word];
            if (acronym) {
                return acronym;
            }
            const lower = word.toLowerCase();
            return index === 0 ? lower.charAt(0).toUpperCase() + lower.slice(1) : lower;
        })
        .join(' ');
}

@Pipe({ name: 'enumLabel', standalone: true })
export class EnumLabelPipe implements PipeTransform {
    transform(value: string | null | undefined): string {
        return labelOfEnum(value);
    }
}