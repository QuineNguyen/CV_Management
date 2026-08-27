import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { AuthService } from "../../services/auth.service";
import { ActivatedRoute, Router } from "@angular/router";
import { ToastService } from "../../services/toast.service";
import { firstValueFrom } from "rxjs";
import { HttpErrorResponse } from "@angular/common/http";
import { changePasswordGroupValidator, evaluatePassword, passwordPolicyValidator, PasswordRule } from "../../validators/password.validator";
import { AppRoute } from "../../enums/app-route.enum";
import { ApiError } from "../../dtos/api-error.dto";
import { messageFor } from "../../models/error-messages.model";
import { sanitizeReturnUrl } from "../../utils/return-url.util";
import { QueryParam } from "../../enums/query-param.enum";

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
    private readonly route = inject(ActivatedRoute);
    private readonly toast = inject(ToastService);
    private readonly returnUrl = sanitizeReturnUrl(
        this.route.snapshot.queryParamMap.get(QueryParam.ReturnUrl),
    )

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
            await firstValueFrom(this.auth.changePassword(this.form.getRawValue()));
            // The server revoked every token, so the current one is already dead.
            this.auth.clearSession();
            this.toast.success('Password changed successfully. Please sign in again');
            await this.router.navigate([AppRoute.Login], {
                queryParams: this.returnUrl ? { [QueryParam.ReturnUrl]: this.returnUrl } : undefined,
            });
        } catch (error) {
            this.toast.error(this.extractErrorMessage(error));
        } finally {
            this.submitting.set(false);
        }
    }

    private extractErrorMessage(error: unknown): string {
        if (error instanceof HttpErrorResponse) {
            const apiError = error.error as ApiError | null;
            if (apiError?.code) {
                return messageFor(apiError.code, apiError.message);
            }
        }
        return messageFor('INTERNAL_ERROR');
    }

    async cancel(): Promise<void> {
        await this.router.navigateByUrl(this.returnUrl ?? AppRoute.Home);
    }
}