import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';

/** The signed-in user's own page. The route is behind authGuard, so `user()` is never null here. */
@Component({
  selector: 'app-account',
  imports: [MatCardModule, MatButtonModule, MatChipsModule, MatIconModule],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout().subscribe({
      // the store clears its state either way (finalize) — leave the page in both cases
      next: () => void this.router.navigateByUrl('/'),
      error: () => void this.router.navigateByUrl('/'),
    });
  }
}
