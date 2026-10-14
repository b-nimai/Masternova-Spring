import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntitlementApi } from './entitlement-api';

describe('EntitlementApi', () => {
  let api: EntitlementApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(EntitlementApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a course page’s locks and a lecture’s access', () => {
    let kind = '';
    api.courseAccess('c1').subscribe((a) => (kind = a.course.kind));
    http.expectOne('/api/v1/courses/c1/access').flush({
      courseId: 'c1',
      course: { kind: 'DENIED', reason: 'NO_ENTITLEMENT' },
      lectures: [],
    });
    expect(kind).toBe('DENIED');

    api.lectureAccess('l1').subscribe();
    expect(http.expectOne('/api/v1/lectures/l1/access').request.method).toBe('GET');
  });

  it('asks for a playback token with an empty POST', () => {
    let url = '';
    api.playback('l1').subscribe((p) => (url = p.manifestUrl));
    const req = http.expectOne('/api/v1/lectures/l1/playback');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({ manifestUrl: '/api/v1/lectures/l1/hls/master.m3u8?token=T', expiresAt: 'x' });
    expect(url).toContain('?token=T');
  });
});
