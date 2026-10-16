import {
  afterNextRender,
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  untracked,
  viewChild,
} from '@angular/core';
import type Hls from 'hls.js';

/** A fatal playback error, and where the viewer was — so a new ticket can resume there. */
export interface PlaybackFailure {
  /** hls.js `details` (e.g. `levelLoadError`), `MEDIA_ERROR` (native), or `HLS_UNSUPPORTED`. */
  details: string;
  /** Seconds into the video when it failed. */
  position: number;
  /** The HTTP status that failed it, when known (hls.js); native HLS doesn't say. */
  status?: number;
}

/** Where the viewer is, each time the video element says so (Phase 10: progress heartbeats). */
export interface PlaybackProgress {
  /** Seconds into the video. */
  position: number;
  playing: boolean;
  event: 'play' | 'pause' | 'seeked' | 'ended' | 'timeupdate';
}

const PROGRESS_EVENTS = ['play', 'pause', 'seeked', 'ended', 'timeupdate'] as const;

/**
 * Plays an HLS master playlist (ADR-0013): hls.js wherever Media Source Extensions exist, the
 * browser's own HLS where they don't (iPhone Safari).
 *
 * ⭐ `afterRenderEffect`: the `<video>` element only exists once the view is in the DOM, and hls.js
 *    touches `window` — so it starts there, never during construction (and never on a server).
 *    As an EFFECT it runs again whenever `src` changes: a re-ticketed url (ADR-0017) reloads the
 *    same `<video>` at `startAt` instead of rebuilding the component.
 * ⭐ hls.js (~150 kB) is loaded with a dynamic `import()`: only pages that play video pay for it.
 * ⭐ Destroyed with the component: an hls.js instance left behind keeps downloading segments.
 */
@Component({
  selector: 'app-hls-player',
  styleUrl: './hls-player.scss',
  templateUrl: './hls-player.html',
})
export class HlsPlayer {
  /** The master playlist url (ticketed). A new url reloads the player. */
  readonly src = input.required<string>();
  /** Where a (re)load starts, in seconds — read when `src` changes. */
  readonly startAt = input(0);
  /** A fatal playback error: the parent may re-issue a ticket and resume at `position`. */
  readonly failed = output<PlaybackFailure>();
  /** Play, pause, seek, end, and ~4 times a second while playing (`timeupdate`). */
  readonly playbackProgress = output<PlaybackProgress>();

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private hls: Hls | null = null;
  private destroyed = false;

  constructor() {
    afterNextRender(() => {
      const video = this.video().nativeElement;
      // native HLS reports its failures on the element (hls.js reports its own, below)
      video.addEventListener('error', () => {
        if (!this.hls) this.fail('MEDIA_ERROR');
      });
      for (const event of PROGRESS_EVENTS) {
        video.addEventListener(event, () =>
          this.playbackProgress.emit({
            position: video.currentTime || 0,
            playing: !video.paused && !video.ended,
            event,
          }),
        );
      }
    });
    afterRenderEffect(() => {
      const src = this.src();
      void this.attach(src, untracked(this.startAt));
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.hls?.destroy();
    });
  }

  private async attach(src: string, startAt: number): Promise<void> {
    const video = this.video().nativeElement;
    const { default: HlsJs } = await import('hls.js');
    // the user closed the player, or a newer url arrived, while hls.js was loading
    if (this.destroyed || src !== this.src()) return;
    this.hls?.destroy();
    this.hls = null;
    if (HlsJs.isSupported()) {
      const hls = new HlsJs({ startPosition: startAt > 0 ? startAt : -1 });
      hls.on(HlsJs.Events.ERROR, (_event, data) => {
        if (data.fatal) this.fail(data.details, data.response?.code);
      });
      hls.loadSource(src);
      hls.attachMedia(video);
      this.hls = hls;
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src; // native HLS: Safari fetches the playlists itself (hence the token)
      if (startAt > 0) {
        video.addEventListener('loadedmetadata', () => (video.currentTime = startAt), {
          once: true,
        });
      }
    } else {
      this.fail('HLS_UNSUPPORTED');
    }
  }

  private fail(details: string, status?: number): void {
    this.failed.emit({
      details,
      position: this.video().nativeElement.currentTime || 0,
      ...(status ? { status } : {}),
    });
  }
}
