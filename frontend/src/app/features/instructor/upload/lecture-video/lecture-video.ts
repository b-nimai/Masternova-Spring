import { DecimalPipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subscription } from 'rxjs';
import { MediaApi } from '../../../../core/api/media-api';
import { asProblem } from '../../../../core/api/problem';
import { HlsPlayer } from '../../../../shared/hls-player/hls-player';
import { ChunkedUploader } from '../chunked-uploader';

/** Where one lecture's video is: what the widget shows. */
export type VideoState =
  | { kind: 'none' }
  | { kind: 'uploading'; percent: number }
  | { kind: 'processing'; percent: number; step: string }
  | { kind: 'ready' }
  | { kind: 'failed'; message: string; resumable: boolean };

const FAILURES: Record<string, string> = {
  MEDIA_UNREADABLE: "This file isn't a video we can read. Try another file.",
  TRANSCODE_FAILED: 'Processing failed. An admin has been notified; try again later.',
  UPLOAD_EXPIRED: 'The upload took too long. Pick the file again to start over.',
};

/**
 * One VIDEO lecture's video, end to end (7.8): pick a file → chunked upload with a progress bar →
 * attach it to the lecture (an ATTACH_MEDIA command, so it's undoable) → live processing progress
 * over the SSE stream → ready.
 *
 * ⭐ The lecture's `assetId` is the input of truth: when it changes (attach, undo, reload) the
 *    widget follows it — a stream for the new asset, or nothing.
 */
@Component({
  imports: [DecimalPipe, HlsPlayer, MatButtonModule, MatIconModule, MatProgressBarModule],
  selector: 'app-lecture-video',
  styleUrl: './lecture-video.scss',
  templateUrl: './lecture-video.html',
})
export class LectureVideo {
  private readonly uploader = inject(ChunkedUploader);
  private readonly media = inject(MediaApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly lectureTitle = input.required<string>();
  readonly assetId = input<string | null>(null);
  /** Emitted once the upload completed: the parent attaches the asset to the lecture. */
  readonly uploaded = output<string>();

  protected readonly state = signal<VideoState>({ kind: 'none' });
  /** The ticketed master url while the preview is open (7.9). */
  protected readonly previewUrl = signal<string | null>(null);
  protected readonly previewError = signal(false);
  private watching: Subscription | null = null;

  constructor() {
    effect(() => {
      const asset = this.assetId();
      untracked(() => this.watch(asset));
    });
  }

  protected pick(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // picking the same file again (to resume) must fire `change` again
    if (!file) return;
    this.state.set({ kind: 'uploading', percent: 0 });
    this.uploader
      .upload(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (p) => {
          if (p.phase === 'uploading') {
            this.state.set({ kind: 'uploading', percent: (100 * p.sentBytes) / p.totalBytes });
          } else if (p.phase === 'done') {
            this.state.set({ kind: 'processing', percent: 0, step: 'Starting' });
            this.uploaded.emit(p.assetId);
          }
        },
        error: (e: unknown) => {
          const code = asProblem(e)?.code;
          this.state.set({
            kind: 'failed',
            message:
              (code && FAILURES[code]) ??
              'The upload was interrupted. Pick the same file again to resume where it stopped.',
            resumable: !code || !FAILURES[code],
          });
        },
      });
  }

  /** Open the preview: ask for a playback ticket, hand its url to the player. */
  protected preview(): void {
    const asset = this.assetId();
    if (!asset) return;
    this.previewError.set(false);
    this.media
      .playback(asset)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (grant) => this.previewUrl.set(grant.masterUrl),
        error: () => this.previewError.set(true),
      });
  }

  protected closePreview(): void {
    this.previewUrl.set(null);
  }

  /** Follow an asset's processing until READY / FAILED (the server closes the stream). */
  private watch(assetId: string | null): void {
    this.watching?.unsubscribe();
    this.watching = null;
    this.previewUrl.set(null);
    if (!assetId) {
      if (this.state().kind !== 'uploading') this.state.set({ kind: 'none' });
      return;
    }
    this.watching = this.media
      .stream(assetId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (e) => {
          if (e.event === 'progress') {
            this.state.update((s) =>
              s.kind === 'processing'
                ? { ...s, step: `${titleCase(e.data.job)} ${e.data.rung}`.trim() }
                : s,
            );
            return;
          }
          const snap = e.data;
          if (snap.status === 'READY') this.state.set({ kind: 'ready' });
          else if (snap.status === 'FAILED')
            this.state.set({
              kind: 'failed',
              message: FAILURES[snap.failureCode ?? ''] ?? 'Processing failed.',
              resumable: false,
            });
          else
            this.state.set({
              kind: 'processing',
              percent: snap.percent,
              step: snap.status === 'UPLOADED' ? 'Queued' : 'Processing',
            });
        },
        error: () =>
          this.state.set({ kind: 'processing', percent: 0, step: 'Reconnect to see progress' }),
      });
  }
}

function titleCase(word: string): string {
  return word.charAt(0) + word.slice(1).toLowerCase();
}
