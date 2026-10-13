import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UploadResponse } from '../../../core/api/media-api';
import { ChunkedUploader, fingerprint, UploadProgress } from './chunked-uploader';

/** A 12-byte "video" cut into parts of 5: 5 + 5 + 2. */
const file = new File([new Uint8Array(12)], 'lecture.mp4', { type: 'video/mp4', lastModified: 7 });

function plan(parts: number[], uploadedBytes = 0): UploadResponse {
  return {
    uploadId: 'u1',
    assetId: 'a1',
    status: 'OPEN',
    sizeBytes: 12,
    partSizeBytes: 5,
    partCount: 3,
    uploadedBytes,
    parts: parts.map((n) => ({ number: n, url: `http://storage/part-${n}` })),
    expiresAt: '2026-10-14T00:00:00Z',
  };
}

describe('ChunkedUploader', () => {
  let uploader: ChunkedUploader;
  let http: HttpTestingController;
  let seen: UploadProgress[];

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    uploader = TestBed.inject(ChunkedUploader);
    http = TestBed.inject(HttpTestingController);
    seen = [];
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('PUTs each missing part straight to storage, then completes with no part list', () => {
    uploader.upload(file).subscribe((p) => seen.push(p));

    const create = http.expectOne('/api/v1/media/uploads');
    expect(create.request.body).toEqual({
      filename: 'lecture.mp4',
      contentType: 'video/mp4',
      sizeBytes: 12,
    });
    expect(create.request.headers.has('Idempotency-Key')).toBe(true);
    create.flush(plan([1, 2, 3]));
    expect(localStorage.getItem(fingerprint(file))).toBe('u1'); // resumable from here on

    const puts = [1, 2, 3].map((n) => http.expectOne(`http://storage/part-${n}`)); // all in flight
    expect(puts.map((p) => (p.request.body as Blob).size)).toEqual([5, 5, 2]);
    expect(puts[0].request.headers.has('Authorization')).toBe(false); // ⭐ never our token to S3
    puts.forEach((p) => p.flush(''));

    const complete = http.expectOne('/api/v1/media/uploads/u1/complete');
    expect(complete.request.body).toBeNull();
    complete.flush({ ...plan([]), status: 'COMPLETED' });

    expect(seen.at(-1)).toEqual({ phase: 'done', assetId: 'a1' });
    expect(seen.filter((p) => p.phase === 'uploading').at(-1)).toMatchObject({ sentBytes: 12 });
    expect(localStorage.getItem(fingerprint(file))).toBeNull();
  });

  it('retries a failed part on its own, with backoff', () => {
    vi.useFakeTimers();
    uploader.upload(file).subscribe((p) => seen.push(p));
    http.expectOne('/api/v1/media/uploads').flush(plan([1]));

    http.expectOne('http://storage/part-1').flush('', { status: 500, statusText: 'oops' });
    vi.advanceTimersByTime(600);
    http.expectOne('http://storage/part-1').flush(''); // the retry

    http.expectOne('/api/v1/media/uploads/u1/complete').flush(plan([]));
    expect(seen.at(-1)?.phase).toBe('done');
  });

  it('an expired part url is replaced by a fresh one from the api, not retried', () => {
    uploader.upload(file).subscribe((p) => seen.push(p));
    http.expectOne('/api/v1/media/uploads').flush(plan([1]));

    http.expectOne('http://storage/part-1').flush('', { status: 403, statusText: 'Expired' });
    const fresh = http.expectOne('/api/v1/media/uploads/u1'); // no blind retry of the dead url
    fresh.flush({ ...plan([1]), parts: [{ number: 1, url: 'http://storage/part-1-fresh' }] });
    http.expectOne('http://storage/part-1-fresh').flush('');
    http.expectOne('/api/v1/media/uploads/u1/complete').flush(plan([]));

    expect(seen.at(-1)?.phase).toBe('done');
  });

  it('a complete refused with 409 forgets the upload, so the next pick starts fresh', () => {
    let failed = false;
    uploader.upload(file).subscribe({ error: () => (failed = true) });
    http.expectOne('/api/v1/media/uploads').flush(plan([]));
    http
      .expectOne('/api/v1/media/uploads/u1/complete')
      .flush({ code: 'UPLOAD_EXPIRED' }, { status: 409, statusText: 'Conflict' });

    expect(failed).toBe(true);
    expect(localStorage.getItem(fingerprint(file))).toBeNull();
  });

  it('resumes the same file from what storage already holds', () => {
    localStorage.setItem(fingerprint(file), 'u1');

    uploader.upload(file).subscribe((p) => seen.push(p));

    http.expectOne('/api/v1/media/uploads/u1').flush(plan([3], 10)); // parts 1 + 2 already there
    http.expectNone('/api/v1/media/uploads'); // no new upload
    http.expectOne('http://storage/part-3').flush('');
    http.expectOne('/api/v1/media/uploads/u1/complete').flush(plan([]));

    expect(seen.find((p) => p.phase === 'uploading')).toMatchObject({
      sentBytes: 12,
      totalBytes: 12,
    });
  });

  it('starts over when the stored upload is gone', () => {
    localStorage.setItem(fingerprint(file), 'old');

    uploader.upload(file).subscribe((p) => seen.push(p));

    http
      .expectOne('/api/v1/media/uploads/old')
      .flush({ code: 'UPLOAD_EXPIRED' }, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/media/uploads').flush(plan([1, 2, 3]));
    [1, 2, 3].forEach((n) => http.expectOne(`http://storage/part-${n}`).flush(''));
    http.expectOne('/api/v1/media/uploads/u1/complete').flush(plan([]));

    expect(seen.at(-1)?.phase).toBe('done');
  });
});
