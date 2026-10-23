import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { asProblem, nullIfNotFound } from '../../../core/api/problem';
import { AuthStore } from '../../../core/auth/auth-store';
import { InstructorApplicationApi } from '../instructor-application-api';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Haptics } from '../../../core/haptics';

/** The apply call's stable codes → what the card says. */
const APPLY_PROBLEMS: Record<string, string> = {
  APPLICATION_PENDING: 'Your application is already waiting for an admin.',
  ALREADY_INSTRUCTOR: 'You can already teach.',
};

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

  /** null = never applied (the API's 404). Not asked for at all once the user can teach. */
  protected readonly application = rxResource({
    params: () => (this.auth.hasAnyRole(['INSTRUCTOR']) ? undefined : true),
    stream: () => this.api.mine().pipe(nullIfNotFound()),
  });
  /** The backend's `InstructorApplication.PITCH_MAX`. */
  protected readonly pitchMax = 500;
  protected readonly pitch = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  /**
   * The one silent refresh: 'done' with the role still missing means it was taken away after the
   * approval (or the refresh failed), and the card offers to apply again instead of waiting forever.
   */
  protected readonly roleCheck = signal<'idle' | 'refreshing' | 'done'>('idle');

  /** What the card says once the application has loaded: one state, one template branch. */
  protected readonly view = computed(() => {
    switch (this.application.value()?.status) {
      case 'PENDING':
        return 'pending';
      case 'APPROVED':
        return this.roleCheck() === 'done' ? 'removed' : 'activating';
      case 'REJECTED':
        return 'rejected';
      default:
        return 'none';
    }
  });

  constructor() {
    effect(() => {
      const approved =
        this.application.hasValue() && this.application.value()?.status === 'APPROVED';
      if (approved && !this.auth.hasAnyRole(['INSTRUCTOR']) && this.roleCheck() === 'idle') {
        this.roleCheck.set('refreshing');
        untracked(() =>
          this.auth
            .refresh()
            .pipe(finalize(() => this.roleCheck.set('done')))
            .subscribe({ error: () => undefined }),
        );
      }
    });
  }

  protected apply(): void {
    this.sending.set(true);
    this.error.set(null);
    this.api
      .apply(this.pitch().trim() || null)
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (filed) => {
          this.haptics.success();
          this.application.set(filed);
        },
        error: (e: unknown) => {
          this.haptics.error();
          this.error.set(
            APPLY_PROBLEMS[asProblem(e)?.code ?? ''] ?? 'Something went wrong. Please try again.',
          );
          this.application.reload();
        },
      });
  }
}
