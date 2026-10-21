import { DestroyRef, inject, Service } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, fromEvent, interval, merge } from 'rxjs';
import { HeartbeatResponse, LearningApi } from '../../core/api/learning-api';
import { PlaybackProgress } from '../../shared/hls-player/hls-player';

/** The player's cadence (ADR-0022): with a 30 s flush, a learner loses at most ~45 s. */
export const HEARTBEAT_MS = 15_000;

/**
 * Tells the server where the learner is in the lecture on screen (API conventions §15). Scoped to
 * the player component (`providers`), so it lives and dies with it.
 *
 * - every {@link HEARTBEAT_MS} WHILE PLAYING, and at once on pause, seek and end;
 * - ⭐ the page closing (`pagehide`) or going to the background (`visibilitychange` → hidden:
 *   on phones often the last event that fires) → `sendBeacon`, with the token the last heartbeat
 *   answered — a normal request may never leave an unloading page;
 * - leaving the player inside the app (`DestroyRef`) → one last normal heartbeat;
 * - the same position twice is sent once: a paused player costs nothing.
 *
 * Failures are swallowed: progress is a bookmark, never an error on screen.
 */
@Service({ autoProvided: false })
export class ProgressTracker {
  private readonly api = inject(LearningApi);

  private lectureId: string | null = null;
  private position = 0;
  private playing = false;
  private lastSent = -1;
  private beaconToken: HeartbeatResponse | null = null;

  constructor() {
    const destroyRef = inject(DestroyRef);
    interval(HEARTBEAT_MS)
      .pipe(
        filter(() => this.playing),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.send());
    merge(
      fromEvent(window, 'pagehide'),
      fromEvent(document, 'visibilitychange').pipe(
        filter(() => document.visibilityState === 'hidden'),
      ),
    )
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.sendBeacon());
    destroyRef.onDestroy(() => this.send());
  }

  /** A new lecture on screen: the previous one gets its last word first. */
  track(lectureId: string): void {
    if (this.lectureId !== lectureId) {
      this.send();
    }
    this.lectureId = lectureId;
    this.position = 0;
    this.playing = false;
    this.lastSent = -1;
    this.beaconToken = null;
  }

  /** What the video element just said. */
  update(progress: PlaybackProgress): void {
    this.position = Math.floor(progress.position);
    this.playing = progress.playing;
    if (progress.event !== 'timeupdate' && progress.event !== 'play') {
      this.send(); // pause, seeked, ended: worth saying now
    }
  }

  private send(): void {
    const lectureId = this.lectureId;
    if (!lectureId || this.position === this.lastSent) {
      return;
    }
    const position = this.position;
    this.lastSent = position;
    // ⭐ an answer that lands after track() moved on belongs to the previous lecture: drop it
    const current = () => this.lectureId === lectureId;
    this.api.heartbeat(lectureId, position).subscribe({
      next: (answer) => {
        if (current()) this.beaconToken = answer;
      },
      error: () => {
        if (current() && this.lastSent === position) this.lastSent = -1; // try again next tick
      },
    });
  }

  private sendBeacon(): void {
    const token = this.beaconToken;
    if (!this.lectureId || this.position === this.lastSent) {
      return;
    }
    if (token && Date.now() < Date.parse(token.beaconExpiresAt)) {
      if (this.api.beacon(token.beaconToken, this.position)) {
        this.lastSent = this.position;
      }
    } else {
      this.send(); // no valid token yet: a normal request, which may or may not make it
    }
  }
}
