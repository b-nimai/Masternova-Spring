import {
  HttpClient,
  HttpDownloadProgressEvent,
  HttpEventType,
  HttpHeaders,
} from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors `CreateUploadRequest`. */
export interface CreateUploadRequest {
  filename: string;
  contentType: string;
  sizeBytes: number;
}

/** Mirrors `UploadResponse` (API conventions §8): the plan + a presigned url per part still to send. */
export interface UploadResponse {
  uploadId: string;
  assetId: string;
  status: 'OPEN' | 'COMPLETED' | 'ABORTED' | 'EXPIRED';
  sizeBytes: number;
  partSizeBytes: number;
  partCount: number;
  uploadedBytes: number;
  parts: { number: number; url: string }[];
  expiresAt: string;
}

export type MediaStatus = 'AWAITING_UPLOAD' | 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED';

/** Mirrors `AssetProgressResponse`. */
export interface AssetProgressResponse {
  assetId: string;
  status: MediaStatus;
  failureCode: string | null;
  percent: number;
  jobs: { type: string; rung: string; status: string; percent: number }[];
}

/** One event of the progress stream (API conventions §9). */
export type AssetEvent =
  | { event: 'snapshot'; data: AssetProgressResponse }
  | { event: 'progress'; data: { job: string; rung: string; percent: number } };

/**
 * Splits streamed SSE text into events. Returns the complete events and the unfinished tail (an
 * event ends with a blank line; a chunk can end anywhere). `:` lines are keep-alive comments.
 */
export function parseSse(text: string): {
  events: { event: string; data: string }[];
  rest: string;
} {
  const blocks = text.split(/\r?\n\r?\n/);
  const rest = blocks.pop() ?? '';
  const events = blocks
    .map((block) => {
      let event = 'message';
      const data: string[] = [];
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      }
      return { event, data: data.join('\n') };
    })
    .filter((e) => e.data !== '');
  return { events, rest };
}

/** The media module's API: resumable uploads and an asset's processing progress. */
@Service()
export class MediaApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/media';

  /** ⭐ Unversioned (nothing exists yet): an Idempotency-Key makes a retried create safe. */
  createUpload(request: CreateUploadRequest, idempotencyKey: string): Observable<UploadResponse> {
    return this.http.post<UploadResponse>(`${this.base}/uploads`, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  /** Progress AND resume: what the provider still lacks, with fresh urls. */
  upload(uploadId: string): Observable<UploadResponse> {
    return this.http.get<UploadResponse>(`${this.base}/uploads/${uploadId}`);
  }

  /** No body: the server reads the part list from storage (ADR-0014). Safe to retry. */
  complete(uploadId: string): Observable<UploadResponse> {
    return this.http.post<UploadResponse>(`${this.base}/uploads/${uploadId}/complete`, null);
  }

  abort(uploadId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/uploads/${uploadId}`);
  }

  asset(assetId: string): Observable<AssetProgressResponse> {
    return this.http.get<AssetProgressResponse>(`${this.base}/assets/${assetId}`);
  }

  /**
   * The SSE progress stream, read through HttpClient — NOT EventSource, which can't send the
   * bearer token (API conventions §9). The fetch backend reports the body as it streams
   * (`partialText`); this parses the new part of it into events. Completes when the server closes
   * the stream (READY or FAILED).
   */
  stream(assetId: string): Observable<AssetEvent> {
    return new Observable<AssetEvent>((subscriber) => {
      let seen = 0;
      let buffer = '';
      const emit = (text: string) => {
        const parsed = parseSse(buffer + text);
        buffer = parsed.rest;
        for (const e of parsed.events) {
          if (e.event === 'snapshot' || e.event === 'progress') {
            subscriber.next({ event: e.event, data: JSON.parse(e.data) } as AssetEvent);
          }
        }
      };
      const sub = this.http
        .get(`${this.base}/assets/${assetId}/stream`, {
          headers: new HttpHeaders({ Accept: 'text/event-stream' }),
          observe: 'events',
          reportProgress: true,
          responseType: 'text',
        })
        .subscribe({
          next: (event) => {
            if (event.type === HttpEventType.DownloadProgress) {
              const partial = (event as HttpDownloadProgressEvent).partialText ?? '';
              emit(partial.slice(seen));
              seen = partial.length;
            } else if (event.type === HttpEventType.Response) {
              const body = event.body ?? '';
              emit(body.slice(seen) + '\n\n');
              subscriber.complete();
            }
          },
          error: (e) => subscriber.error(e),
        });
      return () => sub.unsubscribe();
    });
  }
}
