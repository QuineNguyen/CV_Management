export interface GoogleCredentialResponse {
    credential: string; // the ID token
    select_by?: string;
}

export interface GoogleInitializeConfig {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    cancel_on_tap_outside?: boolean;
}

export interface GoogleButtonConfig {
    type: 'standard' | 'icon';
    theme?: 'outline' | 'filled_blue' | 'filled_black';
    size?: 'small' | 'medium' | 'large';
    text?: 'signin_with' | 'signup_with' | 'continue_with';
    width?: number;
    locale?: string;
}

export interface GoogleAccountsId {
    initialize(config: GoogleInitializeConfig): void;
    renderButton(parent: HTMLElement, config: GoogleButtonConfig): void;
    disableAutoSelect(): void;
}

declare global {
    interface Window {
        google?: {
            accounts: {
                id: GoogleAccountsId
            }
        };
    }
}