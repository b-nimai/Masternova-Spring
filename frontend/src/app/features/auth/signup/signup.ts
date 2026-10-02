import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { asProblem } from '../../../core/api/problem';
import { AuthApi } from '../../../core/auth/auth-api';
import { applyServerErrors, passwordsMatch } from '../form-utils';

@Component({
  selector: 'app-signup',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './signup.html',
  styleUrl: './signup.scss',
})
export class Signup {
  private readonly api = inject(AuthApi);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
      displayName: ['', [Validators.required, Validators.maxLength(100)]],
      // 8–72: the SAME rule as the backend (72 = bcrypt's input limit). The server checks again.
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch('password', 'confirmPassword') },
  );
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Set after success: the address the verification email went to. */
  protected readonly registeredEmail = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, displayName, password } = this.form.getRawValue();
    this.api.signup({ email, displayName, password }).subscribe({
      next: (user) => this.registeredEmail.set(user.email),
      error: (e: unknown) => {
        const problem = asProblem(e);
        if (problem?.code === 'EMAIL_TAKEN') {
          this.form.controls.email.setErrors({
            server: 'An account with this email already exists.',
          });
          this.form.controls.email.markAsTouched();
        } else if (!applyServerErrors(this.form, problem)) {
          this.error.set('Something went wrong. Please try again.');
        }
        this.submitting.set(false);
      },
    });
  }
}
