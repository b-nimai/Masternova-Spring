import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { Role } from '../../core/auth/auth-api';
import { AuthStore } from '../../core/auth/auth-store';
import { initialsOf } from '../../shared/initials';

/**
 * The signed-in user's own page. The route is behind authGuard, so `user()` is never null here.
 * Logging out lives in the dashboard sidebar and the avatar menu (core/auth/sign-out.ts).
 */
@Component({
  selector: 'app-account',
  imports: [RouterLink, MatButtonModule, MatChipsModule, MatIconModule],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  protected readonly auth = inject(AuthStore);

  protected readonly initials = computed(() => initialsOf(this.auth.user()?.displayName ?? ''));

  protected readonly roleLabels: Record<Role, string> = {
    LEARNER: 'Learner',
    INSTRUCTOR: 'Instructor',
    ADMIN: 'Admin',
  };
}
