import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { text } from '../../../../testing/dom';
import { HlsPlayer } from '../../../shared/hls-player/hls-player';
import { LecturePlayer, RETICKET_GRACE_MS } from './lecture-player';

vi.mock('hls.js', () => ({
  default: class {
    static Events = { ERROR: 'hlsError' };
    static isSupported = () => true;
    on = vi.fn();
    loadSource = vi.fn();
    attachMedia = vi.fn();
    destroy = vi.fn();
  },
}));

const TICKET = '/api/v1/lectures/l1/playback';
const RESUME = '/api/v1/me/progress/l1';
const resumeAt = (positionSeconds: number) => ({
  lectureId: 'l1',
  positionSeconds,
  maxPositionSeconds: positionSeconds,
  completed: false,
});

describe('LecturePlayer', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<LecturePlayer>;

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    await TestBed.configureTestingModule({
      imports: [LecturePlayer],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LecturePlayer);
    fixture.componentRef.setInput('slug', 'k8s');
    fixture.componentRef.setInput('lectureId', 'l1');
    fixture.detectChanges();
  });

  /** Most tests start where nothing was watched yet. */
  const fromTheStart = () => http.expectOne(RESUME).flush(resumeAt(0));

  afterEach(() => {
    http.match((r) => r.url.startsWith('/api/v1/courses/')); // the sidebar's course: not under test
    http.verify();
    vi.useRealTimers();
  });

  const player = () => fixture.debugElement.query(By.directive(HlsPlayer));
  const issue = (token: string) =>
    http.expectOne(TICKET).flush({
      manifestUrl: `/api/v1/lectures/l1/hls/master.m3u8?token=${token}`,
      expiresAt: '2026-10-14T10:05:00Z',
    });

  it('asks for a token and plays it', () => {
    fromTheStart();
    issue('T1');
    fixture.detectChanges();

    expect(player().componentInstance.src()).toContain('token=T1');
  });

  it('re-tickets after an expired token and resumes at the same second', () => {
    fromTheStart();
    issue('T1');
    fixture.detectChanges();
    vi.setSystemTime(Date.now() + 6 * 60_000); // the 5-minute token has expired

    player().triggerEventHandler('failed', {
      details: 'levelLoadError',
      position: 754,
      status: 403,
    });
    issue('T2');
    fixture.detectChanges();

    expect(player().componentInstance.src()).toContain('token=T2');
    expect(player().componentInstance.startAt()).toBe(754);
  });

  it('shows a failure right after a fresh token instead of looping', () => {
    fromTheStart();
    issue('T1');
    fixture.detectChanges();
    vi.setSystemTime(Date.now() + RETICKET_GRACE_MS - 1);

    player().triggerEventHandler('failed', { details: 'manifestLoadError', position: 0 });
    fixture.detectChanges();

    expect(text(fixture, 'failed')).toContain('Playback failed');
  });

  it('does not re-ticket a failure that was not the token (a 404)', () => {
    fromTheStart();
    issue('T1');
    fixture.detectChanges();
    vi.setSystemTime(Date.now() + 6 * 60_000);

    player().triggerEventHandler('failed', { details: 'levelLoadError', position: 9, status: 404 });
    fixture.detectChanges();

    expect(text(fixture, 'failed')).toContain('Playback failed');
  });

  it('shows a real denial (a refund) with its reason', () => {
    fromTheStart();
    http
      .expectOne(TICKET)
      .flush(
        { status: 403, code: 'FORBIDDEN', reason: 'ENTITLEMENT_REVOKED' },
        { status: 403, statusText: 'Forbidden' },
      );
    fixture.detectChanges();

    expect(text(fixture, 'denied')).toContain('withdrawn');
  });

  it('explains a video that is still processing', () => {
    fromTheStart();
    http
      .expectOne(TICKET)
      .flush({ status: 409, code: 'MEDIA_NOT_READY' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text(fixture, 'failed')).toContain('still processing');
  });

  it('opens where the learner stopped', () => {
    http.expectOne(RESUME).flush(resumeAt(754));
    issue('T1');
    fixture.detectChanges();

    expect(player().componentInstance.startAt()).toBe(754);
  });

  it('starts at 0 when the resume point cannot be read', () => {
    http.expectOne(RESUME).flush(null, { status: 503, statusText: 'Unavailable' });
    issue('T1');
    fixture.detectChanges();

    expect(player().componentInstance.startAt()).toBe(0);
  });

  it('reports a pause to the server at once', () => {
    fromTheStart();
    issue('T1');
    fixture.detectChanges();

    player().triggerEventHandler('playbackProgress', {
      position: 61.7,
      playing: false,
      event: 'pause',
    });

    const beat = http.expectOne((r) => r.method === 'PUT' && r.url === RESUME);
    expect(beat.request.body).toEqual({ positionSeconds: 61 });
    beat.flush({ beaconToken: 'b', beaconExpiresAt: '2026-10-16T10:02:00Z' });
  });
});
