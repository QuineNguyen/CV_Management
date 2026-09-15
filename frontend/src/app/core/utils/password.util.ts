import { AbstractControl, ValidationErrors, ValidatorFn } from "@angular/forms";

export const PASSWORD_MIN_LENGTH = 8;

// Mirrors the backend PasswordCharset enum
export enum PasswordRule {
    MinLength = 'minLength',
    Uppercase = 'uppercase',
    Lowercase = 'lowercase',
    Digit = 'digit',
    Special = 'special',
}

const PATTERNS: Record<Exclude<PasswordRule, PasswordRule.MinLength>, RegExp> = {
    [PasswordRule.Uppercase]: /[A-Z]/,
    [PasswordRule.Lowercase]: /[a-z]/,
    [PasswordRule.Digit]: /\d/,
    [PasswordRule.Special]: /[^A-Za-z0-9]/,
};

// Return the rules the value currently satisfies, for the live checklist.
export function evaluatePassword(value: string): Record<PasswordRule, boolean> {
    return {
        [PasswordRule.MinLength]: value.length >= PASSWORD_MIN_LENGTH,
        [PasswordRule.Uppercase]: PATTERNS[PasswordRule.Uppercase].test(value),
        [PasswordRule.Lowercase]: PATTERNS[PasswordRule.Lowercase].test(value),
        [PasswordRule.Digit]: PATTERNS[PasswordRule.Digit].test(value),
        [PasswordRule.Special]: PATTERNS[PasswordRule.Special].test(value),
    }
}

export const passwordPolicyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string;
    if (!value) {
        return null; // `required` owns the empty case
    }
    const passed = evaluatePassword(value);
    return Object.values(passed).every(Boolean) ? null : { passwordPolicy: true};
}

// Cross-field: new password must differ from the current one and match its confirmation
export function changePasswordGroupValidator(
    currentKey: string,
    newKey: string,
    confirmKey: string,
): ValidatorFn {
    return (group: AbstractControl): ValidationErrors | null => {
        const current = group.get(currentKey)?.value;
        const next = group.get(newKey)?.value;
        const confirm = group.get(confirmKey)?.value;

        const errors: ValidationErrors = {};
        if (next && confirm && next !== confirm) {
            errors['confirmationMismatch'] = true;
        }
        if (current && next && current === next) {
            errors['sameAsOld'] = true;
        }
        return Object.keys(errors).length ? errors : null;
    }
}