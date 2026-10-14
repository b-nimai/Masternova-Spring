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

  afterEach(() => {
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
    issue('T1');
    fixture.detectChanges();

    expect(player().componentInstance.src()).toContain('token=T1');
  });

  it('re-tickets after an expired token and resumes at the same second', () => {
    issue('T1');
    fixture.detectChanges();
    vi.setSystemTime(Date.now() + 6 * 60_000); // the 5-minute token has expired

    player().triggerEventHandler('failed', { details: 'levelLoadError', position: 754 });
    issue('T2');
    fixture.detectChanges();

    expect(player().componentInstance.src()).toContain('token=T2');
    expect(player().componentInstance.startAt()).toBe(754);
  });

  it('shows a failure right after a fresh token instead of looping', () => {
    issue('T1');
    fixture.detectChanges();
    vi.setSystemTime(Date.now() + RETICKET_GRACE_MS - 1);

    player().triggerEventHandler('failed', { details: 'manifestLoadError', position: 0 });
    fixture.detectChanges();

    expect(text(fixture, 'failed')).toContain('Playback failed');
  });

  it('shows a real denial (a refund) with its reason', () => {
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
    http
      .expectOne(TICKET)
      .flush({ status: 409, code: 'MEDIA_NOT_READY' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text(fixture, 'failed')).toContain('still processing');
  });
});
