import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, input, OnDestroy, output, PLATFORM_ID, signal, viewChild } from "@angular/core";
import { ImageService } from "../../services/image.service";
import { ToastService } from "../../services/toast.service";
import { isPlatformBrowser } from "@angular/common";
import { AvatarChange } from "../../models/avatar-change.model";

/*
 * Pick a file, crop it square, upload it, hand the caller back an image id.
 * 
 * The component never writes the id anywhere - it only emits. Where that id lands differs by
 * screen: a CV draft takes it immediately, a user profile only gets it once a reviewer approves
 * and this component has no business knowing which.
 * 
 * Cropper.js is loaded lazily inside the browser guard: it touches document at import time, which
 * breaks server-side rendering.
 */
@Component({
    selector: 'app-avatar-upload',
    standalone: true,
    templateUrl: './avatar-upload.component.html',
    styleUrl: './avatar-upload.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvatarUploadComponent implements OnDestroy {

    private static readonly MAX_BYTES = 5 * 1024 * 1024;
    private static readonly ACCEPTED_TYPES = ['image/jpeg', 'image/png'];
    private static readonly MAX_EDGE = 512;
    private static readonly OUTPUT_QUALITY = 0.85;
    private static readonly CLOSE_ANIMATION_MS = 500;

    private readonly imageService = inject(ImageService);
    private readonly toast = inject(ToastService);
    private readonly platformId = inject(PLATFORM_ID);

    private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');
    private readonly cropImage = viewChild<ElementRef<HTMLImageElement>>('cropImage');

    readonly avatarUrl = input<string | null>(null);
    readonly disabled = input(false);

    // Label on the button when there is no avatar yet; screens word this differently.
    readonly emptyLabel = input('Upload photo');

    readonly avatarChanged = output<AvatarChange>();

    readonly cropOpen = signal(false);
    readonly isCropClosing = signal(false);
    readonly uploading = signal(false);
    readonly sourceUrl = signal<string | null>(null);

    private cropper: { getCroppedCanvas(options: object): HTMLCanvasElement; destroy(): void } | null = null;
    private cropBackdropMouseDownTarget: EventTarget | null = null;

    // ---------- File selection ----------

    openFilePicker(): void {
        if (this.disabled() || this.uploading()) {
            return;
        }
        this.fileInput()?.nativeElement.click();
    }

    /*
     * Validated here as well as on the server. The server is the authority, but a 5 MB round trip
     * that ends in a rejection is a poor way to tell someone their photo is too big.
     */
    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        // Reset immediately so picking the same file twice in a row still fires a change event.
        input.value = '';

        if (!file) {
            return;
        }
        if (!AvatarUploadComponent.ACCEPTED_TYPES.includes(file.type)) {
            this.toast.error('Only JPEG and PNG images are accepted');
            return;
        }
        if (file.size > AvatarUploadComponent.MAX_BYTES) {
            this.toast.error('Image must be 5 MB or smaller');
            return;
        }

        this.revokeSource();
        this.sourceUrl.set(URL.createObjectURL(file));
        this.isCropClosing.set(false);
        this.cropOpen.set(true);
    }

    // ---------- Cropper lifecycle ----------

    /*
     * Bound to the <img> load event rather than run after opening the modal: Cropper reads the
     * element's natural dimensions, which are zero until the object URL has actually decoded.
     */
    async onCropImageLoaded(): Promise<void> {
        if (!isPlatformBrowser(this.platformId)) {
            return;
        }
        const element = this.cropImage()?.nativeElement;
        if (!element) {
            return;
        }

        this.destroyCropper();
        const { default: Cropper } = await import('cropperjs');

        this.cropper = new Cropper(element, {
            aspectRatio: 1,
            viewMode: 1,
            dragMode: 'move',
            autoCropArea: 0.8,
            preview: '.crop-preview',
            guides: false,
            center: true,
            responsive: true,
            background: false,
            checkOrientation: true,
        });
    }

    confirmCrop(): void {
        if (!this.cropper || this.uploading()) {
            return;
        }
        const canvas = this.cropper.getCroppedCanvas({
            width: AvatarUploadComponent.MAX_EDGE,
            height: AvatarUploadComponent.MAX_EDGE,
            imageSmoothingQuality: 'high',
            // A transparent PNG flattens to black in JPEG without this.
            fillColor: '#ffffff',
        });

        this.uploading.set(true);
        canvas.toBlob(
            blob => {
                if (!blob) {
                    this.uploading.set(false);
                    this.toast.error('The image could not be processed');
                    return;
                }
                this.upload(blob);
            },
            'image/jpeg',
            AvatarUploadComponent.OUTPUT_QUALITY,
        );
    }

    /*
     * No error branch: the interceptor renders whichever code came back - too large, wrong type,
     * or the store being unreachable. A second toast here would just duplicate it.
     */
    private upload(blob: Blob): void {
        this.imageService.upload(blob).subscribe({
            next: result => {
                this.avatarChanged.emit({ imageId: result.id, presignedUrl: result.presignedUrl });
                this.uploading.set(false);
                this.closeCrop();
            },
            // Stay on the crop dialog so the crop is not lost and the user can retry.
            error: () => this.uploading.set(false),
        });
    }

    // ---------- Dialog ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.cropOpen()) {
            this.cancelCrop();
        }
    }

    onCropBackdropMouseDown(event: MouseEvent): void {
        this.cropBackdropMouseDownTarget = event.target;
    }

    onCropBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget
            && this.cropBackdropMouseDownTarget === event.currentTarget
        ) {
            this.cancelCrop();
        }
        this.cropBackdropMouseDownTarget = null;
    }

    cancelCrop(): void {
        if (this.isCropClosing() || this.uploading()) {
            return;
        }
        this.isCropClosing.set(true);
        setTimeout(() => this.closeCrop(), AvatarUploadComponent.CLOSE_ANIMATION_MS);
    }

    private closeCrop(): void {
        this.destroyCropper();
        this.revokeSource();
        this.cropOpen.set(false);
        this.isCropClosing.set(false);
    }

    private destroyCropper(): void {
        this.cropper?.destroy();
        this.cropper = null;
    }

    // Object URLs hold the whole file in memory until released.
    private revokeSource(): void {
        const url = this.sourceUrl();
        if (url) {
            URL.revokeObjectURL(url);
            this.sourceUrl.set(null);
        }
    }

    ngOnDestroy(): void {
        this.destroyCropper();
        this.revokeSource();
    }
}