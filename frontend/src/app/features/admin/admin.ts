import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { asProblem } from '../../core/api/problem';
import { Role, User } from '../../core/auth/auth-api';
import { AdminApi } from './admin-api';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** A custom validator is just a function: control → errors or null. */
export function uuidValidator(control: AbstractControl<string>): ValidationErrors | null {
  return !control.value || UUID.test(control.value.trim()) ? null : { uuid: true };
}

/** A group-level validator: at least one box ticked (the backend requires ≥ 1 role too). */
function atLeastOneChecked(group: AbstractControl): ValidationErrors | null {
  return Object.values(group.value as Record<string, boolean>).some(Boolean)
    ? null
    : { noRole: true };
}

export const ALL_ROLES: readonly Role[] = ['LEARNER', 'INSTRUCTOR', 'ADMIN'];

/** Look up a user by id, then change their roles. */
@Component({
  selector: 'app-admin',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
  ],
  templateUrl: './admin.html',
  styleUrl: './admin.scss',
})
export class Admin {
  private readonly api = inject(AdminApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly roles = ALL_ROLES;
  protected readonly lookup = this.fb.group({ id: ['', [Validators.required, uuidValidator]] });
  // ⭐ a typed group of booleans: rolesForm.value.ADMIN is `boolean | undefined`, a typo won't compile
  protected readonly rolesForm = this.fb.group(
    { LEARNER: false, INSTRUCTOR: false, ADMIN: false },
    { validators: atLeastOneChecked },
  );

  protected readonly user = signal<User | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saved = signal(false);
  protected readonly busy = signal(false);

  protected find(): void {
    if (this.lookup.invalid) {
      this.lookup.markAllAsTouched();
      return;
    }
    this.start();
    this.user.set(null);
    this.api.getUser(this.lookup.getRawValue().id.trim()).subscribe({
      next: (user) => this.show(user),
      error: (e: unknown) => this.fail(e),
    });
  }

  protected save(): void {
    const user = this.user();
    if (!user || this.rolesForm.invalid) {
      return;
    }
    this.start();
    const value = this.rolesForm.getRawValue();
    const roles = ALL_ROLES.filter((role) => value[role]);
    this.api.changeRoles(user.id, roles).subscribe({
      next: (updated) => {
        this.show(updated);
        this.saved.set(true);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  private start(): void {
    this.busy.set(true);
    this.error.set(null);
    this.saved.set(false);
  }

  private show(user: User): void {
    this.user.set(user);
    // reflect the SERVER's answer in the checkboxes (it's the source of truth)
    this.rolesForm.setValue({
      LEARNER: user.roles.includes('LEARNER'),
      INSTRUCTOR: user.roles.includes('INSTRUCTOR'),
      ADMIN: user.roles.includes('ADMIN'),
    });
    this.rolesForm.markAsPristine();
    this.busy.set(false);
  }

  private fail(e: unknown): void {
    // ⭐ branch on the stable `code`, never on the human-readable `detail`
    const messages: Record<string, string> = {
      NOT_FOUND: 'No user with that id.',
      CANNOT_DEMOTE_SELF: "You can't remove your own ADMIN role.",
      INSUFFICIENT_ROLE: 'You need the ADMIN role for this.',
    };
    const code = asProblem(e)?.code;
    this.error.set((code && messages[code]) ?? 'Something went wrong. Please try again.');
    this.busy.set(false);
  }
}
