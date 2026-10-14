const IPHONE = /iPhone|iPod/i;

/*
 * True when a PDF can be shown inside the page.
 * - Android Chrome reports pdfViewerEnabled = false and would show an empty frame.
 * - iPhone Safari reports true but renders only the first page in a frame.
 * Both get "Open" and "Download" instead; iPad and desktop browsers preview inline.
 */
export function canPreviewPdfInline(): boolean {
    if (typeof navigator === 'undefined') {
        return false;
    }
    return navigator.pdfViewerEnabled !== false && !IPHONE.test(navigator.userAgent);
}