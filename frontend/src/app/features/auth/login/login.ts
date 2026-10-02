import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { asProblem } from '../../../core/api/problem';
import { AuthStore } from '../../../core/auth/auth-store';
import { safeReturnUrl } from '../form-utils';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** `?returnUrl=/account` — bound by withComponentInputBinding() (app.config.ts). */
  readonly returnUrl = input<string>();

  // ⭐ TYPED reactive form: form.value.email is `string`, not `any` (NonNullableFormBuilder:
  //    reset() returns to '' instead of null).
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // show every field's error at once
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (e: unknown) => {
        // the server deliberately gives ONE answer for unknown email and wrong password
        this.error.set(
          asProblem(e)?.code === 'INVALID_CREDENTIALS'
            ? 'Email or password is incorrect.'
            : 'Something went wrong. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }
}
