import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { catchError, of, throwError } from 'rxjs';
import { asProblem, isNotFound } from '../../../core/api/problem';
import { AuthStore } from '../../../core/auth/auth-store';
import { InstructorApplicationApi } from '../instructor-application-api';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Haptics } from '../../../core/haptics';

/**
 * "Teach on Masternova" (the account page): apply to teach, see the application wait, or — once an
 * admin approved it — go to your courses. Not in the Figma, which lets signup pick a role; here
 * no one picks a role (docs/lld/identity.md §5), so this is where a learner asks.
 *
 * ⭐ Roles live in the access token. When the application says APPROVED but the token still says
 * learner, one silent refresh picks the new role up instead of waiting out the 15 minutes.
 */
@Component({
  selector: 'app-teach-card',
  imports: [
    Skeleton,
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './teach-card.html',
  styleUrl: './teach-card.scss',
})
export class TeachCard {
  private readonly api = inject(InstructorApplicationApi);
  private readonly haptics = inject(Haptics);
  protected readonly auth = inject(AuthStore);

  /** null = never applied (the API's 404). */
  protected readonly application = rxResource({
    stream: () =>
      this.api
        .mine()
        .pipe(catchError((e: unknown) => (isNotFound(e) ? of(null) : throwError(() => e)))),
  });
  protected readonly pitch = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  private refreshedForRole = false;

  constructor() {
    effect(() => {
      const approved =
        this.application.hasValue() && this.application.value()?.status === 'APPROVED';
      if (approved && !this.auth.hasAnyRole(['INSTRUCTOR']) && !this.refreshedForRole) {
        this.refreshedForRole = true;
        untracked(() => this.auth.refresh().subscribe({ error: () => undefined }));
      }
    });
  }

  protected apply(): void {
    this.sending.set(true);
    this.error.set(null);
    this.api.apply(this.pitch().trim() || null).subscribe({
      next: (filed) => {
        this.haptics.success();
        this.application.set(filed);
        this.sending.set(false);
      },
      error: (e: unknown) => {
        this.haptics.error();
        const code = asProblem(e)?.code;
        this.error.set(
          code === 'APPLICATION_PENDING'
            ? 'Your application is already waiting for an admin.'
            : code === 'ALREADY_INSTRUCTOR'
              ? 'You can already teach.'
              : 'Something went wrong. Please try again.',
        );
        this.application.reload();
        this.sending.set(false);
      },
    });
  }
}
