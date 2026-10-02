import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import {
  CATEGORY_COPY,
  NotificationApi,
  NotificationCategory,
} from '../../core/api/notification-api';
import { AuthStore } from '../../core/auth/auth-store';

type UnsubscribeState = 'confirm' | 'working' | 'done' | 'invalid' | 'missing';

/**
 * Landing page of the footer link in an optional email: /unsubscribe?token=…
 *
 * ⭐ Nothing happens on load. Mail scanners and link previewers open every URL in an email (some
 * even run JavaScript); if loading this page unsubscribed, they would unsubscribe people. A person
 * clicks the button → the page POSTs. (Gmail's own button uses the one-click header instead.)
 */
@Component({
  imports: [RouterLink, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  selector: 'app-unsubscribe',
  styleUrl: './unsubscribe.scss',
  templateUrl: './unsubscribe.html',
})
export class Unsubscribe implements OnInit {
  private readonly api = inject(NotificationApi);
  protected readonly auth = inject(AuthStore);

  /** The `?token=` query param (withComponentInputBinding). */
  readonly token = input<string>();
  protected readonly state = signal<UnsubscribeState>('confirm');
  protected readonly category = signal<NotificationCategory | null>(null);
  protected readonly copy = CATEGORY_COPY;

  ngOnInit(): void {
    if (!this.token()) {
      this.state.set('missing');
    }
  }

  protected confirm(): void {
    const token = this.token();
    if (!token) {
      return;
    }
    this.state.set('working');
    this.api.unsubscribe(token).subscribe({
      next: ({ category }) => {
        this.category.set(category);
        this.state.set('done');
      },
      // UNSUBSCRIBE_TOKEN_INVALID (422): forged, expired or malformed — one answer for all
      error: () => this.state.set('invalid'),
    });
  }
}
