import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import type Hls from 'hls.js';

/**
 * Plays an HLS master playlist (ADR-0013): hls.js wherever Media Source Extensions exist, the
 * browser's own HLS where they don't (iPhone Safari).
 *
 * ⭐ `afterNextRender`: the `<video>` element only exists once the view is in the DOM, and hls.js
 *    touches `window` — so it starts there, never during construction (and never on a server).
 * ⭐ hls.js (~150 kB) is loaded with a dynamic `import()`: only pages that play video pay for it.
 * ⭐ Destroyed with the component: an hls.js instance left behind keeps downloading segments.
 */
@Component({
  selector: 'app-hls-player',
  styleUrl: './hls-player.scss',
  templateUrl: './hls-player.html',
})
export class HlsPlayer {
  /** The master playlist url (ticketed: `…/master.m3u8?t=…`). Fixed for the instance. */
  readonly src = input.required<string>();
  /** A fatal playback error (hls.js `details`, or `HLS_UNSUPPORTED`): the parent may re-issue. */
  readonly failed = output<string>();

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private hls: Hls | null = null;
  private destroyed = false;

  constructor() {
    afterNextRender(() => void this.attach());
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.hls?.destroy();
    });
  }

  private async attach(): Promise<void> {
    const video = this.video().nativeElement;
    const { default: HlsJs } = await import('hls.js');
    if (this.destroyed) return; // the user closed the preview while hls.js was loading
    if (HlsJs.isSupported()) {
      const hls = new HlsJs();
      hls.on(HlsJs.Events.ERROR, (_event, data) => {
        if (data.fatal) this.failed.emit(data.details);
      });
      hls.loadSource(this.src());
      hls.attachMedia(video);
      this.hls = hls;
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = this.src(); // native HLS: Safari fetches the playlists itself (hence the ticket)
    } else {
      this.failed.emit('HLS_UNSUPPORTED');
    }
  }
}
