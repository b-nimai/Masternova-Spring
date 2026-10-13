import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AssetEvent, MediaApi, parseSse, RECONNECT_MS } from './media-api';

describe('parseSse', () => {
  it('splits complete events and keeps the unfinished tail', () => {
    const { events, rest } = parseSse(
      'event:snapshot\ndata:{"a":1}\n\n: keep-alive\n\nevent:progress\ndata:{"b"',
    );
    expect(events).toEqual([{ event: 'snapshot', data: '{"a":1}' }]); // the comment is no event
    expect(rest).toBe('event:progress\ndata:{"b"');
  });

  it('joins multi-line data and defaults the event name', () => {
    expect(parseSse('data: one\ndata: two\n\n').events).toEqual([
      { event: 'message', data: 'one\ntwo' },
    ]);
  });
});

describe('MediaApi', () => {
  let api: MediaApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(MediaApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates an upload with an Idempotency-Key and completes it with no body', () => {
    api
      .createUpload({ filename: 'a.mp4', contentType: 'video/mp4', sizeBytes: 10 }, 'k1')
      .subscribe();
    const create = http.expectOne('/api/v1/media/uploads');
    expect(create.request.headers.get('Idempotency-Key')).toBe('k1');

    api.complete('u1').subscribe();
    const complete = http.expectOne('/api/v1/media/uploads/u1/complete');
    expect(complete.request.method).toBe('POST');
    expect(complete.request.body).toBeNull();
  });

  it('turns a streamed body into events, across chunk boundaries, and completes with it', () => {
    const events: AssetEvent[] = [];
    let done = false;
    api.stream('a1').subscribe({ next: (e) => events.push(e), complete: () => (done = true) });

    const req = http.expectOne('/api/v1/media/assets/a1/stream');
    expect(req.request.headers.get('Accept')).toBe('text/event-stream');
    const first = 'event:snapshot\ndata:{"status":"PROCESSING","percent":10}\n\nevent:pro';
    req.event({ type: HttpEventType.DownloadProgress, loaded: 1, partialText: first });
    expect(events).toHaveLength(1);

    const second = first + 'gress\ndata:{"job":"TRANSCODE","rung":"720p","percent":55}\n\n';
    req.event({ type: HttpEventType.DownloadProgress, loaded: 2, partialText: second });
    const full = second + 'event:snapshot\ndata:{"status":"READY","percent":100}\n\n';
    req.flush(full);

    expect(events.map((e) => e.event)).toEqual(['snapshot', 'progress', 'snapshot']);
    expect(events[1].data).toEqual({ job: 'TRANSCODE', rung: '720p', percent: 55 });
    expect(done).toBe(true);
  });

  describe('follow', () => {
    const processing =
      'event:snapshot\ndata:{"status":"PROCESSING","percent":30,"failureCode":null,"jobs":[]}\n\n';
    const ready =
      'event:snapshot\ndata:{"status":"READY","percent":100,"failureCode":null,"jobs":[]}\n\n';

    afterEach(() => vi.useRealTimers());

    it('reopens a stream the server closed before the asset finished, and stops at READY', () => {
      vi.useFakeTimers();
      const statuses: string[] = [];
      let done = false;
      api.follow('a1').subscribe({
        next: (e) => e.event === 'snapshot' && statuses.push(e.data.status),
        complete: () => (done = true),
      });

      http.expectOne('/api/v1/media/assets/a1/stream').flush(processing); // closed at 30 min
      vi.advanceTimersByTime(RECONNECT_MS);
      http.expectOne('/api/v1/media/assets/a1/stream').flush(ready);
      vi.advanceTimersByTime(RECONNECT_MS);

      http.expectNone('/api/v1/media/assets/a1/stream'); // ⭐ finished: no third stream
      expect(statuses).toEqual(['PROCESSING', 'READY']);
      expect(done).toBe(true);
    });

    it('rides out a dropped connection but not a refusal', () => {
      vi.useFakeTimers();
      let error: unknown;
      api.follow('a1').subscribe({ error: (e) => (error = e) });

      http.expectOne('/api/v1/media/assets/a1/stream').error(new ProgressEvent('error')); // status 0
      vi.advanceTimersByTime(RECONNECT_MS);
      http
        .expectOne('/api/v1/media/assets/a1/stream')
        .flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

      expect(error).toBeDefined();
    });
  });
});
