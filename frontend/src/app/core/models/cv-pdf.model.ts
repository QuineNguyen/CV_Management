import { CvLanguage } from "../enums/cv-language.enum";

// One published version of one CV, as handed to the PDF preview
export interface CvPdfTarget {
    cvId: string;
    versionId: string;
    versionNumber: number;
    employeeName: string | null;
    language: CvLanguage;
}

// A signed link ready to open: full URL and the expiry on the browser's own clock
export interface CvPdfLink {
    url: string;
    fileName: string;
    // Epoch milliseconds
    expiresAt: number;
}