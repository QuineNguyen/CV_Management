import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { AuthService } from "../../services/auth.service";
import { Router } from "@angular/router";
import { MatSnackBar } from "@angular/material/snack-bar";
import { changePasswordGroupValidator, evaluatePassword, passwordPolicyValidator, PasswordRule } from "../../validators/password.validator";
import { AppRoute } from "../../enums/app-route.enum";

@Component({
    selector: 'app-change-password',
    standalone: true,
    imports: [
        ReactiveFormsModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
    ],
    templateUrl: './change-password.component.html',
    styleUrl: './change-password.component.css',
})
export class ChangePasswordComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly auth = inject(AuthService);
    private readonly router = inject(Router);
    private readonly snackBar = inject(MatSnackBar);

    readonly rules = PasswordRule;
    readonly submitting = signal(false);
    readonly hideCurrent = signal(true);
    readonly hideNew = signal(true);
    readonly checklist = signal(evaluatePassword(''));

    // Set when the account was forced here rather than choosing to change its password.
    readonly forced = this.auth.mustChangePassword();

    readonly form = this.formBuilder.nonNullable.group(
        {
            currentPassword: ['', Validators.required],
            newPassword: ['', [Validators.required, passwordPolicyValidator]],
            confirmPassword: ['', Validators.required],
        },
        { validators: changePasswordGroupValidator('currentPassword', 'newPassword', 'confirmPassword')},
    );

    constructor() {
        this.form.controls.newPassword.valueChanges.subscribe((value) =>
            this.checklist.set(evaluatePassword(value ?? '')),
        );
    }

    async submit(): Promise<void> {
        if (this.form.invalid || this.submitting()) {
            this.form.markAllAsTouched();
            return;
        }

        this.submitting.set(true);
        try {
            await this.auth.changePassword(this.form.getRawValue());
            // The server revoked every token, so the current one is already dead.
            this.auth.clearSession();
            this.snackBar.open('Password changed successfully. Please sign in again.', 'Close', {
                duration: 5000,
            });
            await this.router.navigate([AppRoute.Login]);
        } finally {
            this.submitting.set(false);
        }
    }

    async cancel(): Promise<void> {
        await this.router.navigate([AppRoute.Home]);
    }
}