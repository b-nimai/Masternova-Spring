import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { DenialReason, EntitlementApi } from '../../../core/api/entitlement-api';
import { asProblem } from '../../../core/api/problem';
import { DENIAL_MESSAGES, isDenialReason } from '../../../shared/denial-messages';
import { HlsPlayer, PlaybackFailure } from '../../../shared/hls-player/hls-player';

/** What the page shows — a discriminated union, switched on in the template. */
type PlayerState =
  | { kind: 'loading' }
  | { kind: 'playing'; url: string; startAt: number }
  | { kind: 'denied'; reason: DenialReason }
  | { kind: 'failed'; message: string };

/** A failure this soon after a fresh ticket is a real problem, not an expired token. */
export const RETICKET_GRACE_MS = 30_000;

const FAILURES: Record<string, string> = {
  MEDIA_NOT_READY: 'This video is still processing. Try again in a few minutes.',
  LECTURE_HAS_NO_VIDEO: 'This lecture has no video.',
};

/**
 * /courses/:slug/lectures/:lectureId — plays one lecture (ADR-0017).
 *
 * ⭐ RE-TICKETING: the playback token lives 5 minutes and is bound to this network. When a
 *    playlist answers 403 (an ABR switch 40 minutes in, a new Wi-Fi), the page asks for a new token —
 *    which re-runs the entitlement decision on the server — and resumes at the same second. A
 *    failure within {@link RETICKET_GRACE_MS} of a fresh token is shown instead of retried, and a
 *    403 on the re-ticket is a REAL denial (a refund): shown, never looped.
 */
@Component({
  imports: [HlsPlayer, MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink],
  selector: 'app-lecture-player',
  styleUrl: './lecture-player.scss',
  templateUrl: './lecture-player.html',
})
export class LecturePlayer {
  private readonly api = inject(EntitlementApi);

  readonly slug = input.required<string>();
  readonly lectureId = input.required<string>();

  protected readonly state = signal<PlayerState>({ kind: 'loading' });
  protected readonly denialMessages = DENIAL_MESSAGES;
  private ticketedAt = 0;
  private request: Subscription | null = null;

  constructor() {
    effect(() => {
      this.lectureId(); // a new lecture → a new ticket from the start
      untracked(() => this.ticket(0));
    });
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  protected onFailed(failure: PlaybackFailure): void {
    // ⭐ hls.js knows the HTTP status: only a 403 (expired token, new network) is worth a
    //    re-ticket. Native Safari doesn't say, so it falls back to the time since the ticket.
    const notTheToken = failure.status !== undefined && failure.status !== 403;
    if (notTheToken || Date.now() - this.ticketedAt < RETICKET_GRACE_MS) {
      this.state.set({ kind: 'failed', message: 'Playback failed. Please try again.' });
      return;
    }
    this.ticket(failure.position); // most likely an expired token: re-ticket, resume
  }

  protected retry(): void {
    this.ticket(0);
  }

  private ticket(startAt: number): void {
    this.request?.unsubscribe();
    this.request = this.api.playback(this.lectureId()).subscribe({
      next: (ticket) => {
        this.ticketedAt = Date.now();
        this.state.set({ kind: 'playing', url: ticket.manifestUrl, startAt });
      },
      error: (error: unknown) => {
        const problem = asProblem(error);
        if (problem?.status === 403 && isDenialReason(problem.reason)) {
          this.state.set({ kind: 'denied', reason: problem.reason });
        } else {
          this.state.set({
            kind: 'failed',
            message: FAILURES[problem?.code ?? ''] ?? 'We could not start this video.',
          });
        }
      },
    });
  }
}
