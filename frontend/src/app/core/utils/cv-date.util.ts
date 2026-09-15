import { Pipe, PipeTransform } from "@angular/core";
import { CvLanguage } from "../enums/cv-language.enum";

// Full date for a single point in time; month and year for a range endpoint.
export type CvDateStyle = 'full' | 'monthYear';

const EN_MONTHS_LONG = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
];

const EN_MONTHS_SHORT = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

/*
 * Renders a stored ISO date in the conventions of the CV's language.
 *
 * - Presentation only. Dates live in content_json as ISO 8601 and are never rewritten into a
 * local format: a published version is immutable, so a formatted date could never be re-rendered
 * for another language, could not be sorted or compared and would make the same day read as two
 * different values when the version diff compares languages.
 * - The three formats are hand-written rather than delegated to Angular's locale data. CV date
 * conventions are fixed and few and registering locale bundles adds a way for the output to
 * differ between environments - the last thing wanted in a document sent to a customer.
 */
@Pipe({
    name: 'cvDate',
    standalone: true,
    pure: true,
})
export class CvDatePipe implements PipeTransform {

    transform(isoDate: string | Date | null | undefined,
            language: CvLanguage,
            style: CvDateStyle = 'full'
    ): string {
        if (!isoDate) {
            return '';
        }

        let year: number;
        let month: number;
        let day: number;

        if (isoDate instanceof Date) {
            // matDatepicker supplies a Date object; extract local components directly.
            year = isoDate.getFullYear();
            month = isoDate.getMonth() + 1;
            day = isoDate.getDate();
        } else {
            // Parsed by hand, not through Date: 'new Date("1995-03-15")' is read as UTC midnight and
            // shifts to the previous day for anyone west of Greenwich.
            const [yearText, monthText, dayText] = isoDate.split('-');
            year = Number(yearText);
            month = Number(monthText);
            day = Number(dayText);
        }

        if (!year || !month || (style === 'full' && !day)) {
            return typeof isoDate === 'string' ? isoDate : '';
        }
        
        return style === 'monthYear'
            ? this.monthYear(language, year, month)
            : this.full(language, year, month, day);
    }

    private full(language: CvLanguage, year: number, month: number, day: number): string {
        switch (language) {
            case CvLanguage.Vi:
                return `${this.pad(day)}/${this.pad(month)}/${year}`;
            case CvLanguage.Ja:
                return `${year}年${month}月${day}日`;
            default:
                return `${day} ${EN_MONTHS_LONG[month - 1]} ${year}`;
        }
    }

    private monthYear(language: CvLanguage, year: number, month: number): string {
        switch (language) {
            case CvLanguage.Vi:
                return `${this.pad(month)}/${year}`;
            case CvLanguage.Ja:
                return `${year}年${month}月`;
            default:
                return `${EN_MONTHS_SHORT[month - 1]} ${year}`;
        }
    }

    private pad(value: number): string {
        return value < 10 ? `0${value}` : String(value);
    }
}