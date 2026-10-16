import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createEnvironmentInjector, EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LearningApi } from '../../core/api/learning-api';
import { HEARTBEAT_MS, ProgressTracker } from './progress-tracker';

const BEAT = '/api/v1/me/progress/l1';
const NOW = Date.parse('2026-10-16T10:00:00Z');

describe('ProgressTracker', () => {
  let http: HttpTestingController;
  let scope: EnvironmentInjector; // ⭐ stands in for the player component's injector
  let tracker: ProgressTracker;
  let sendBeacon: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    sendBeacon = vi.spyOn(TestBed.inject(LearningApi), 'beacon').mockReturnValue(true);
    scope = createEnvironmentInjector([ProgressTracker], TestBed.inject(EnvironmentInjector));
    tracker = scope.get(ProgressTracker);
    tracker.track('l1');
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  const play = (position: number) =>
    tracker.update({ position, playing: true, event: 'timeupdate' });
  const answer = (expiresInMs = 120_000) =>
    http.expectOne(BEAT).flush({
      beaconToken: 'tok',
      beaconExpiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    });

  it('beats every 15 s while playing, with the whole second', () => {
    play(3.9);
    vi.advanceTimersByTime(HEARTBEAT_MS);

    const beat = http.expectOne(BEAT);
    expect(beat.request.method).toBe('PUT');
    expect(beat.request.body).toEqual({ positionSeconds: 3 });
    beat.flush({ beaconToken: 'tok', beaconExpiresAt: new Date(NOW + 120_000).toISOString() });
  });

  it('says a pause at once, and stays silent while paused', () => {
    play(40);
    tracker.update({ position: 41, playing: false, event: 'pause' });
    answer();

    vi.advanceTimersByTime(HEARTBEAT_MS * 4);
    http.expectNone(BEAT);
  });

  it('sends the same position only once', () => {
    tracker.update({ position: 90, playing: false, event: 'seeked' });
    answer();
    tracker.update({ position: 90, playing: false, event: 'pause' });
    http.expectNone(BEAT);
  });

  it('uses the beacon when the page goes away', () => {
    tracker.update({ position: 10, playing: false, event: 'pause' });
    answer();
    play(25);

    window.dispatchEvent(new Event('pagehide'));

    expect(sendBeacon).toHaveBeenCalledWith('tok', 25);
    http.expectNone(BEAT);
  });

  it('falls back to a normal request when the beacon token has expired', () => {
    tracker.update({ position: 10, playing: false, event: 'pause' });
    answer(1_000);
    play(25);
    vi.setSystemTime(Date.now() + 2_000);

    window.dispatchEvent(new Event('pagehide'));

    expect(sendBeacon).not.toHaveBeenCalled();
    answer();
  });

  it('says where the learner left when the player is destroyed', () => {
    play(70);
    scope.destroy();

    expect(http.expectOne(BEAT).request.body).toEqual({ positionSeconds: 70 });
    vi.advanceTimersByTime(HEARTBEAT_MS * 2); // and the interval died with it
    http.expectNone(BEAT);
  });

  it('a failed beat is retried at the next tick', () => {
    play(30);
    vi.advanceTimersByTime(HEARTBEAT_MS);
    http.expectOne(BEAT).flush(null, { status: 503, statusText: 'Unavailable' });

    vi.advanceTimersByTime(HEARTBEAT_MS);
    answer();
  });
});
