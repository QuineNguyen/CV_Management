import { inject, Injectable } from "@angular/core";
import { MatSnackBar, MatSnackBarConfig, MatSnackBarRef, TextOnlySnackBar } from "@angular/material/snack-bar";
import { ToastType } from "../enums/toast-type.enum";

@Injectable({ providedIn: 'root' })
export class ToastService {

    private static readonly DEFAULT_ACTION = 'Dismiss';
    private static readonly SUCCESS_DURATION_MS = 3000;
    private static readonly ERROR_DURATION_MS = 6000;

    private readonly snackBar = inject(MatSnackBar);

    success(message: string, action?: string): MatSnackBarRef<TextOnlySnackBar> {
        return this.show(message, ToastType.Success, ToastService.SUCCESS_DURATION_MS, action);
    }

    error(message: string, action?: string): MatSnackBarRef<TextOnlySnackBar> {
        return this.show(message, ToastType.Error, ToastService.ERROR_DURATION_MS, action);
    }

    info(message: string, action?: string): MatSnackBarRef<TextOnlySnackBar> {
        return this.show(message, ToastType.Info, ToastService.SUCCESS_DURATION_MS, action);
    }

    private show(
        message: string, 
        type: ToastType, 
        duration: number,
        action?: string,
    ): MatSnackBarRef<TextOnlySnackBar> {
        return this.snackBar.open(message, action ?? ToastService.DEFAULT_ACTION, {
            duration,
            horizontalPosition: 'end',
            verticalPosition: 'bottom',
            politeness: type === ToastType.Error ? 'assertive' : 'polite',
            panelClass: ['toast', `toast-${type}`],
        })
    }
}