import { DestroyRef, signal } from "@angular/core";
import { Observable } from "rxjs";
import { ToastService } from "../../services/toast.service";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

/*
 * Load / refresh state shared by the three dashboards.
 * Errors are already toasted by the error interceptor, so a failure only resets the flags here.
 */
export class DashboardState<T> {
    readonly data = signal<T | null>(null);
    readonly loading = signal(true);
    readonly refreshing = signal(false);

    constructor(
        private readonly fetch: () => Observable<T>,
        private readonly toast: ToastService,
        private readonly destroyRef: DestroyRef,
    ) {}

    // First load or retry: skeleton until the figures arrive
    load(): void {
        this.loading.set(true);
        this.request(false);
    }
    
    // Manual refresh: the current figures stay on screen meanwhile
    refresh(): void {
        if (this.refreshing() || this.loading()) {
            return;
        }
        this.refreshing.set(true);
        this.request(true);
    }

    private request(manual: boolean): void {
        this.fetch()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.data.set(data);
                    this.settle();
                    if (manual) {
                        this.toast.success('Dashboard refreshed');
                    }
                },
                error: () => this.settle(),
            });
    }

    private settle(): void {
        this.loading.set(false);
        this.refreshing.set(false);
    }
}