import { inject } from "@angular/core";
import { CanDeactivateFn } from "@angular/router";
import { ConfirmService } from "./confirm.service";

// Implemented by any page holding editable state worth protecting.
export interface HasUnsavedChanges {
    hasUnsavedChanges(): boolean;
}

/*
 * Blocks in-app navigation away from a form with unsaved edits.
 *
 * - The browser's own beforeunload prompt only covers closing the tab or reloading. Clicking a
 * sidebar link is a route change, which never reaches it - and losing a half-filled CV that way is
 * a real loss, not a small annoyance.
 * - Deliberately generic: any component implementing HasUnsavedChanges gets the same protection
 * without a guard of its own.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = component => {
    if (!component.hasUnsavedChanges()) {
        return true;
    }
    return inject(ConfirmService).ask({
        title: 'Leave without saving?',
        message: 'Your changes have not been saved. Leaving now discards them',
        confirmLabel: 'Discard changes',
        cancelLabel: 'Stay on this page',
        danger: true,
    });
}