import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import {
  CATEGORY_COPY,
  NotificationApi,
  NotificationCategory,
  NotificationPreferenceResponse,
} from '../../../core/api/notification-api';

type LoadState = 'loading' | 'ready' | 'error';

/**
 * /account/notifications — one toggle per email category.
 *
 * ⭐ OPTIMISTIC UI: a toggle flips immediately and the PUT runs in the background. If the server
 * refuses, the toggle flips back and a snackbar says so. The user never waits on a spinner for a
 * change that almost always succeeds.
 */
@Component({
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
  ],
  selector: 'app-notifications',
  styleUrl: './notifications.scss',
  templateUrl: './notifications.html',
})
export class Notifications implements OnInit {
  private readonly api = inject(NotificationApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly copy = CATEGORY_COPY;
  protected readonly state = signal<LoadState>('loading');
  protected readonly preferences = signal<NotificationPreferenceResponse[]>([]);
  /** Categories with a PUT in flight — their toggle is disabled so two PUTs can't race. */
  protected readonly saving = signal<ReadonlySet<NotificationCategory>>(new Set());

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.api.preferences().subscribe({
      next: (preferences) => {
        this.preferences.set(preferences);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected toggle(category: NotificationCategory, enabled: boolean): void {
    this.setEnabled(category, enabled); // ⭐ 1. show the new state now
    this.markSaving(category, true);
    this.api
      .setPreference(category, enabled)
      .pipe(finalize(() => this.markSaving(category, false)))
      .subscribe({
        error: () => {
          this.setEnabled(category, !enabled); // ⭐ 2. roll back to what the server still has
          this.snackBar.open(
            `Couldn't save "${this.copy[category].label}". Please try again.`,
            'Dismiss',
            { duration: 5000 },
          );
        },
      });
  }

  /** Immutable update: a new array, so signal consumers see the change. */
  private setEnabled(category: NotificationCategory, enabled: boolean): void {
    this.preferences.update((list) =>
      list.map((p) => (p.category === category ? { ...p, enabled } : p)),
    );
  }

  private markSaving(category: NotificationCategory, saving: boolean): void {
    this.saving.update((set) => {
      const next = new Set(set);
      if (saving) {
        next.add(category);
      } else {
        next.delete(category);
      }
      return next;
    });
  }
}
