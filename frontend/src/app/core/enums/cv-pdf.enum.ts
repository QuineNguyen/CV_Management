// Lifecycle of one PDF preview request
export enum PdfPreviewStatus {
    Loading = 'LOADING',
    Ready = 'READY',
    Failed = 'FAILED',
}

// Content types the client produces or inspects
export enum MediaType {
    Json = 'application/json',
    Pdf = 'application/pdf',
}