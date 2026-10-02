import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { Problem, fieldErrors } from '../../core/api/problem';

/**
 * ⭐ A CROSS-FIELD validator: it runs on the whole GROUP, because no single control can know
 * whether two passwords match. The error lands on the group (`form.errors.passwordMismatch`).
 */
export function passwordsMatch(passwordKey: string, confirmKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordKey)?.value;
    const confirm = group.get(confirmKey)?.value;
    return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
  };
}

/**
 * Puts the server's field errors (a 400 Problem's `errors[]`) onto the matching form controls, so
 * they show under the right input like client-side errors do. Returns true if any matched.
 */
export function applyServerErrors(form: FormGroup, problem: Problem | null): boolean {
  let applied = false;
  for (const [field, message] of Object.entries(fieldErrors(problem))) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...control.errors, server: message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}

/**
 * ⭐ Open-redirect protection: only same-site PATHS are allowed as a return target.
 * "/account" ✅  "https://evil.example" ❌  "//evil.example" ❌ (protocol-relative = another site!)
 */
export function safeReturnUrl(url: string | null | undefined, fallback = '/account'): string {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\')
    ? url
    : fallback;
}
