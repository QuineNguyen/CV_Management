import { ChangeDetectionStrategy, Component, computed, ElementRef, HostListener, inject, input, OnDestroy, OnInit, output, signal, viewChild } from "@angular/core";
import { CvService } from "../../../services/cv.service";
import { ToastService } from "../../../services/toast.service";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { CvPdfLink, CvPdfTarget } from "../../../models/cv-pdf.model";
import { PdfPreviewStatus } from "../../../enums/cv-pdf.enum";
import { CV_LANGUAGE_LABELS } from "../../../enums/cv-language.enum";
import { canPreviewPdfInline } from "../../../utils/pdf-viewer.util";
import { Subscription } from "rxjs";

/*
 * Preview of one published version exactly as the customer receives it.
 * - The frame and "Open in new tab" load a signed link from the server, not a blob: URL. The
 * link ends with the file name and the server names the file too, so the browser's own PDF
 * toolbar saves "CV_..._v2.pdf" rather than a random id.
 * - A link lives a few minutes; any action that needs one renews it first when it is about to
 * expire. Renewing is a new export and is audited as one.
 * - Errors are toasted by the interceptor; the dialog only shows a retry state.
 */
@Component({
    selector: 'app-cv-pdf-preview-dialog',
    standalone: true,
    imports: [],
    templateUrl: './cv-pdf-preview-dialog.component.html',
    styleUrl: './cv-pdf-preview-dialog.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvPdfPreviewDialogComponent implements OnInit, OnDestroy {

    // Matches the modal float-out animation in _ui.scss
    private static readonly CLOSE_DELAY_MS = 200;
    // A link this close to expiry is renewed before it is used
    private static readonly EXPIRY_MARGIN_MS = 30_000;
    // Asks the server for an attachment instead of an inline view
    private static readonly DOWNLOAD_QUERY = '?download=true';
    // The empty document a frame starts with; its load event is not the PDF arriving
    private static readonly BLANK_PAGE = 'about:blank';

    private readonly cvService = inject(CvService);
    private readonly toast = inject(ToastService);
    private readonly sanitizer = inject(DomSanitizer);

    readonly target = input.required<CvPdfTarget>();
    readonly closed = output<void>();

    readonly Status = PdfPreviewStatus;
    readonly languageLabels = CV_LANGUAGE_LABELS;

    // Decided once: the device does not change while the dialog is open
    readonly inlineViewer = canPreviewPdfInline();

    readonly status = signal<PdfPreviewStatus>(PdfPreviewStatus.Loading);
    readonly isClosing = signal(false);
    readonly frameLoaded = signal(false);

    private readonly link = signal<CvPdfLink | null>(null);

    readonly ready = computed(() => this.status() === PdfPreviewStatus.Ready && !!this.link());

    // Spinner while the link is issued, then until the frame has the PDF
    readonly busy = computed(() => this.status() === PdfPreviewStatus.Loading
        || (this.status() === PdfPreviewStatus.Ready && this.inlineViewer && !this.frameLoaded()));

    // The URL comes from our own API base plus a path the server built
    readonly frameUrl = computed<SafeResourceUrl | null>(() => {
        const link = this.link();
        return link ? this.sanitizer.bypassSecurityTrustResourceUrl(link.url) : null;
    });

    readonly fileName = computed(() => this.link()?.fileName ?? '');

    private readonly frame = viewChild<ElementRef<HTMLIFrameElement>>('pdfFrame');
    private request: Subscription | null = null;
    private backdropMouseDownTarget: EventTarget | null = null;

    ngOnInit(): void {
        this.load();
    }

    ngOnDestroy(): void {
        this.request?.unsubscribe();
    }

    // ---------- Loading ----------

    load(): void {
        this.status.set(PdfPreviewStatus.Loading);
        this.requestLink(() => undefined);
    }

    onFrameLoad(frame: HTMLIFrameElement): void {
        if (!this.isBlank(frame)) {
            this.frameLoaded.set(true);
        }
    }

    // ---------- Actions ----------

    // The server sends an attachment named like the file; download keeps the name same-origin too
    download(): void {
        this.withFreshLink(link => {
            const anchor = document.createElement('a');
            anchor.href = link.url + CvPdfPreviewDialogComponent.DOWNLOAD_QUERY;
            anchor.download = link.fileName;
            anchor.click();
            this.toast.success(`Downloading ${link.fileName}`);
        });
    }

    // Prints the frame's document; a browser that refuses gets the PDF in its own tab instead
    print(): void {
        const frameWindow = this.frame()?.nativeElement.contentWindow;
        if (!frameWindow) {
            this.openInNewTab();
            return;
        }
        try {
            frameWindow.focus();
            frameWindow.print();
        } catch {
            this.openInNewTab();
        }
    }

    // The tab's address ends with the file name and so does its title
    openInNewTab(): void {
        this.withFreshLink(link => window.open(link.url, '_blank', 'noopener'));
    }

    // ---------- Closing ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.close();
    }

    onBackdropMouseDown(event: MouseEvent): void {
        this.backdropMouseDownTarget = event.target;
    }

    // Only a press that also started on the backdrop closes; a drag out of the dialog does not
    onBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget && this.backdropMouseDownTarget === event.currentTarget) {
            this.close();
        }
        this.backdropMouseDownTarget = null;
    }

    close(): void {
        if (this.isClosing()) {
            return;
        }
        this.isClosing.set(true);
        setTimeout(() => this.closed.emit(), CvPdfPreviewDialogComponent.CLOSE_DELAY_MS);
    }

    // ---------- Link ----------

    private withFreshLink(action: (link: CvPdfLink) => void): void {
        const link = this.link();
        if (link && Date.now() < link.expiresAt - CvPdfPreviewDialogComponent.EXPIRY_MARGIN_MS) {
            action(link);
            return;
        }
        this.requestLink(action);
    }

    // A new link means a new frame URL, so the frame reloads and the spinner shows again
    private requestLink(then: (link: CvPdfLink) => void): void {
        const { cvId, versionId } = this.target();
        this.request?.unsubscribe();

        this.request = this.cvService.createPdfLink(cvId, versionId).subscribe({
            next: link => {
                this.frameLoaded.set(false);
                this.link.set(link);
                this.status.set(PdfPreviewStatus.Ready);
                then(link);
            },
            error: () => this.status.set(PdfPreviewStatus.Failed),
        });
    }

    // Reading the location of a cross-origin frame throws; that frame has left the blank page anyway
    private isBlank(frame: HTMLIFrameElement): boolean {
        try {
            return frame.contentWindow?.location.href === CvPdfPreviewDialogComponent.BLANK_PAGE;
        } catch {
            return false;
        }
    }
}