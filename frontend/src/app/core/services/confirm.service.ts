import { Injectable, signal } from "@angular/core";

export interface ConfirmOptions {
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    // Styles the confirm button as destructive.
    danger?: boolean;
}

interface PendingConfirm extends ConfirmOptions {
    // Called by whichever button the user presses; this is what releases the caller.
    settle: (answer: boolean) => void;
}

/*
 * Asks a yes/no question from somewhere that has no template of its own - a route guard, mainly.
 * 
 * - The question is held here as a signal; ShellComponent renders it with the same modal markup
 * every page uses, so a guard's prompt is indistinguishable from an inline one. That matters
 * because this prompt appears exactly when someone is about to lose work: a dialog that looks
 * foreign at that moment is one people dismiss by reflex.
 * - Splitting "who asks" from "who draws" is what makes it possible. The caller gets a promise
 * and never learns where the answer came from.
 * - One question at a time: a second ask while one is open answers the first as cancelled rather
 * than stacking dialogs a user cannot tell apart.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {

    private readonly pendingSignal = signal<PendingConfirm | null>(null);

    readonly pending = this.pendingSignal.asReadonly();

    ask(options: ConfirmOptions): Promise<boolean> {
        // Nothing may stay unsettled: an abandoned promise leaves its guard waiting forever.
        this.answer(false);

        return new Promise<boolean>(resolve => {
            this.pendingSignal.set({
                ...options,
                settle: answer => {
                    this.pendingSignal.set(null);
                    resolve(answer);
                },
            });
        });
    }

    // Called by the shell's buttons. A no-op when nothing is pending.
    answer(result: boolean): void {
        this.pendingSignal()?.settle(result);
    }
}