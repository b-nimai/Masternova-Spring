import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LearningApi } from './learning-api';

describe('LearningApi', () => {
  let api: LearningApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(LearningApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  it('PUTs a free enrollment on the pair', () => {
    api.enroll('c1').subscribe();
    expect(http.expectOne('/api/v1/me/enrollments/c1').request.method).toBe('PUT');
  });

  it('GETs the dashboard, a course and a lecture', () => {
    api.dashboard().subscribe();
    api.courseProgress('c1').subscribe();
    api.lectureProgress('l1').subscribe();
    http.expectOne('/api/v1/me/learning');
    http.expectOne('/api/v1/me/courses/c1/progress');
    http.expectOne('/api/v1/me/progress/l1');
  });

  it('PUTs a heartbeat with the position', () => {
    api.heartbeat('l1', 42).subscribe();
    const req = http.expectOne('/api/v1/me/progress/l1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ positionSeconds: 42 });
  });

  it('sends the beacon with sendBeacon, token in a JSON body', async () => {
    const sendBeacon = vi.fn(() => true);
    vi.stubGlobal('navigator', { sendBeacon });

    expect(api.beacon('tok', 99)).toBe(true);

    const [url, body] = sendBeacon.mock.calls[0] as unknown as [string, Blob];
    expect(url).toBe('/api/v1/progress/beacon');
    expect(body.type).toBe('application/json');
    expect(JSON.parse(await body.text())).toEqual({ token: 'tok', positionSeconds: 99 });
  });
});
