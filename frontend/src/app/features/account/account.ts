import { ViewportScroller } from '@angular/common';
import { afterNextRender, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Role } from '../../core/auth/auth-api';
import { AuthStore } from '../../core/auth/auth-store';
import { initialsOf } from '../../shared/initials';
import { TeachCard } from './teach-card/teach-card';

/**
 * The signed-in user's own page. The route is behind authGuard, so `user()` is never null here.
 * Logging out lives in the dashboard sidebar and the avatar menu (core/auth/sign-out.ts).
 */
@Component({
  selector: 'app-account',
  imports: [RouterLink, MatButtonModule, MatChipsModule, MatIconModule, TeachCard],
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

  constructor() {
    // `#teach` (the home page's "Become an Instructor" for a learner). The page scrolls to the
    // top on arrival (scroll-to-top.ts leaves fragments alone), so scroll to the card once it's drawn.
    const scroller = inject(ViewportScroller);
    if (inject(ActivatedRoute).snapshot.fragment === 'teach') {
      afterNextRender(() => scroller.scrollToAnchor('teach'));
    }
  }
}
