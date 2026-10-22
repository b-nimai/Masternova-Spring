import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';

/**
 * The signed-in user's own page. The route is behind authGuard, so `user()` is never null here.
 * Logging out lives in the dashboard sidebar and the avatar menu (core/auth/sign-out.ts).
 */
@Component({
  selector: 'app-account',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatChipsModule, MatIconModule],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  protected readonly auth = inject(AuthStore);
}
