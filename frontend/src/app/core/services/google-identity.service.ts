import { Injectable, PLATFORM_ID, inject } from "@angular/core";
import { isPlatformBrowser } from "@angular/common";
import { environment } from "../../../environments/environment";
import { GoogleAccountsId, GoogleButtonConfig, GoogleCredentialResponse } from "../dtos/google-identity.dto";

/*
 * Thin wrapper over Google Identity Services.
 * The GSI script is loaded async in index.html, so it may not be ready when a component
 * initialises; `waitForScript` polls until it is instead of failing silently. 
 */
@Injectable({ providedIn: 'root' })
export class GoogleIdentityService {
    private readonly platformId = inject(PLATFORM_ID);
    private initialised = false;
    private onCredential?: (idToken: string) => void;

    private static readonly POLL_INTERVAL_MS = 100;
    private static readonly POLL_TIMEOUT_MS = 5000;
    private static readonly MIN_BUTTON_WIDTH = 200;
    private static readonly MAX_BUTTON_WIDTH = 400;
    private static readonly DEFAULT_BUTTON_WIDTH = 320;

    private get clientId(): string {
        return (window as any).__env?.GOOGLE_CLIENT_ID || environment.googleClientId || '';
    }

    get isEnabled(): boolean {
        return isPlatformBrowser(this.platformId) && !!this.clientId;
    }

    // Renders the official Google button into `container` and reports the ID token.
    async renderButton(
        container: HTMLElement,
        onCredential: (idToken: string) => void,
        config: Partial<GoogleButtonConfig> = {}
    ): Promise<void> {
        if (!this.isEnabled) {
            return;
        }
        const accounts = await this.waitForScript();
        if (!accounts) {
            console.warn('[GoogleIdentity] GSI script did not load within timeout');
            return;
        }

        this.onCredential = onCredential;
        if (!this.initialised) {
            accounts.initialize({
                client_id: this.clientId,
                callback: (response: GoogleCredentialResponse) => this.onCredential?.(response.credential),
                cancel_on_tap_outside: true,
            });
            this.initialised = true;
        }

        accounts.renderButton(container, {
            type: 'standard',
            theme: 'outline',
            size: 'large',
            text: 'signin_with',
            width: this.clampWidth(container.clientWidth),
            locale: 'vi',
            ...config,
        });

        setTimeout(() => {
            if (!container.childElementCount) {
                console.warn('[GoogleIdentity] renderButton produced no output', {
                    width: this.clampWidth(container.clientWidth),
                    connected: container.isConnected,
                });
            }
        }, 500);
    }

    private clampWidth(measured: number): number {
        const width = measured || GoogleIdentityService.DEFAULT_BUTTON_WIDTH;
        return Math.min(
            Math.max(Math.floor(width), GoogleIdentityService.MIN_BUTTON_WIDTH),
            GoogleIdentityService.MAX_BUTTON_WIDTH,
        );
    }

    // Stops Google from auto-selecting the last account after an explicit sign-out.
    disableAutoSelect(): void {
        if (this.isEnabled) {
            window.google?.accounts.id.disableAutoSelect();
        }
    }

    private waitForScript(): Promise<GoogleAccountsId | null> {
        return new Promise((resolve) => {
            const started = Date.now();
            const poll = () => {
                if (window.google?.accounts?.id) {
                    resolve(window.google.accounts.id);
                    return;
                }
                if (Date.now() - started > GoogleIdentityService.POLL_TIMEOUT_MS) {
                    resolve(null);
                    return;
                }
                setTimeout(poll, GoogleIdentityService.POLL_INTERVAL_MS);
            };
            poll();
        });
    }
}