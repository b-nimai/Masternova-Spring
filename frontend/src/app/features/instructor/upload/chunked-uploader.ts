import { HttpClient, HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import {
  catchError,
  concat,
  defer,
  filter,
  from,
  map,
  mergeMap,
  Observable,
  of,
  retry,
  scan,
  switchMap,
  tap,
  throwError,
  timer,
} from 'rxjs';
import { MediaApi, UploadResponse } from '../../../core/api/media-api';

/** What the upload widget shows. */
export type UploadProgress =
  | { phase: 'uploading'; assetId: string; sentBytes: number; totalBytes: number }
  | { phase: 'completing'; assetId: string }
  | { phase: 'done'; assetId: string };

/** Parts in flight at once: enough to fill a home uplink, few enough not to starve the tab. */
export const PARALLEL_PARTS = 4;
const PART_RETRIES = 4;

/**
 * Uploads one video straight to storage (media LLD §5): the api hands out a PLAN and presigned
 * part urls; the bytes go browser → storage, `PARALLEL_PARTS` at a time.
 *
 * ⭐ Resume: the upload id is kept in localStorage under the file's fingerprint (name, size, last
 *    modified). Picking the same file again — after a reload, a crash, a closed laptop — asks the
 *    api what storage already holds (ADR-0014) and sends only the rest.
 * ⭐ A failed part is retried with backoff (0.5 s, 1 s, 2 s, 4 s) on its own; the others keep
 *    going. If it still fails, the upload errors out and stays resumable.
 * ⭐ Complete sends no part list: the server asks storage, so a client can't claim a part it never
 *    sent.
 */
@Service()
export class ChunkedUploader {
  private readonly api = inject(MediaApi);
  private readonly http = inject(HttpClient);

  upload(file: File): Observable<UploadProgress> {
    const key = fingerprint(file);
    return this.start(file, key).pipe(
      switchMap((plan) =>
        concat(
          this.sendParts(file, plan),
          of<UploadProgress>({ phase: 'completing', assetId: plan.assetId }),
          defer(() => this.api.complete(plan.uploadId)).pipe(
            tap({
              next: () => forget(key),
              // a 409 (expired, aborted) means this upload is over: the next pick starts fresh
              error: (e: unknown) => {
                if (e instanceof HttpErrorResponse && e.status === 409) forget(key);
              },
            }),
            map((): UploadProgress => ({ phase: 'done', assetId: plan.assetId })),
          ),
        ),
      ),
    );
  }

  /** Resume the stored upload for this file if the api still has it OPEN, else start a new one. */
  private start(file: File, key: string): Observable<UploadResponse> {
    const stored = recall(key);
    const fresh = defer(() =>
      this.api
        .createUpload(
          {
            filename: file.name,
            contentType: file.type || 'video/mp4',
            sizeBytes: file.size,
          },
          crypto.randomUUID(),
        )
        .pipe(tap((plan) => remember(key, plan.uploadId))),
    );
    if (!stored) {
      return fresh;
    }
    return this.api.upload(stored).pipe(
      catchError((e: unknown) => {
        // 404 / 409 (completed, aborted, expired): that upload is over — start a new one
        if (e instanceof HttpErrorResponse && (e.status === 404 || e.status === 409)) {
          forget(key);
          return fresh;
        }
        throw e;
      }),
    );
  }

  /** PUT every part still missing; emits the running byte count. */
  private sendParts(file: File, plan: UploadResponse): Observable<UploadProgress> {
    const loaded = new Map<number, number>();
    return from(plan.parts).pipe(
      mergeMap(
        (part) =>
          this.putPart(file, plan, part.number, part.url).pipe(
            map((bytes) => ({ part: part.number, bytes })),
          ),
        PARALLEL_PARTS,
      ),
      scan((total, { part, bytes }) => {
        loaded.set(part, bytes);
        return plan.uploadedBytes + [...loaded.values()].reduce((a, b) => a + b, 0);
      }, plan.uploadedBytes),
      map((sentBytes): UploadProgress => ({
        phase: 'uploading',
        assetId: plan.assetId,
        sentBytes,
        totalBytes: plan.sizeBytes,
      })),
    );
  }

  /**
   * One part: the bytes of its slice, with its own retries. Emits bytes sent so far.
   *
   * ⭐ A 403 means the presigned url EXPIRED (they live an hour; a 10 GiB upload takes longer):
   *    retrying it is pointless, so ask the api for a fresh one, once (found in review).
   */
  private putPart(
    file: File,
    plan: UploadResponse,
    number: number,
    url: string,
    refreshed = false,
  ): Observable<number> {
    const start = (number - 1) * plan.partSizeBytes;
    const blob = file.slice(start, Math.min(start + plan.partSizeBytes, file.size));
    return defer(() =>
      this.http.put(url, blob, { observe: 'events', reportProgress: true, responseType: 'text' }),
    ).pipe(
      filter((e) => e.type === HttpEventType.UploadProgress || e.type === HttpEventType.Response),
      map((e) => (e.type === HttpEventType.UploadProgress ? e.loaded : blob.size)),
      retry({
        count: PART_RETRIES,
        delay: (e, attempt) => (isExpired(e) ? throwError(() => e) : timer(250 * 2 ** attempt)),
      }),
      catchError((e: unknown) => {
        if (refreshed || !isExpired(e)) throw e;
        return this.api.upload(plan.uploadId).pipe(
          switchMap((fresh) => {
            const part = fresh.parts.find((p) => p.number === number);
            // not listed as missing: storage already has it (an earlier attempt landed)
            return part ? this.putPart(file, plan, number, part.url, true) : of(blob.size);
          }),
        );
      }),
    );
  }
}

function isExpired(e: unknown): boolean {
  return e instanceof HttpErrorResponse && e.status === 403;
}

/** Which file this is, across reloads: the same file picked again resumes. */
export function fingerprint(file: File): string {
  return `masternova.upload:${file.name}:${file.size}:${file.lastModified}`;
}

// localStorage can be missing or throw (private mode, blocked storage): resuming is a nicety
function remember(key: string, uploadId: string): void {
  try {
    localStorage.setItem(key, uploadId);
  } catch {
    /* no resume, the upload itself still works */
  }
}

function recall(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function forget(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    /* nothing to forget */
  }
}
