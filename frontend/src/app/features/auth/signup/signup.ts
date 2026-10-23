import { Component, inject, input, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { asProblem } from '../../../core/api/problem';
import { AuthApi } from '../../../core/auth/auth-api';
import { AuthShell } from '../auth-shell/auth-shell';
import { applyServerErrors, passwordsMatch } from '../form-utils';

type JoinAs = 'STUDENT' | 'INSTRUCTOR';

@Component({
  selector: 'app-signup',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    AuthShell,
  ],
  templateUrl: './signup.html',
  styleUrl: './signup.scss',
})
export class Signup implements OnInit {
  private readonly api = inject(AuthApi);

  /** `?as=instructor` (the home page's "Become an Instructor") opens on the Instructor tab. */
  readonly as = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      // The Figma's Student / Instructor tabs. Not a role: Instructor files an application that
      // an admin decides on (the account is a learner either way).
      joinAs: ['STUDENT' as JoinAs],
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
  /** One toggle shows both password fields, so the user can compare them. */
  protected readonly showPasswords = signal(false);
  /** Set after success: the address the verification email went to. */
  protected readonly registeredEmail = signal<string | null>(null);
  protected readonly appliedAsInstructor = signal(false);

  ngOnInit(): void {
    if (this.as() === 'instructor') {
      this.form.controls.joinAs.setValue('INSTRUCTOR');
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, displayName, password, joinAs } = this.form.getRawValue();
    const applyAsInstructor = joinAs === 'INSTRUCTOR';
    this.api.signup({ email, displayName, password, applyAsInstructor }).subscribe({
      next: (user) => {
        this.appliedAsInstructor.set(applyAsInstructor);
        this.registeredEmail.set(user.email);
      },
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
