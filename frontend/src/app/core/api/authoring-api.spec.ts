import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthoringApi } from './authoring-api';

describe('AuthoringApi', () => {
  let api: AuthoringApi;
  let http: HttpTestingController;
  const base = '/api/v1/instructor/courses';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuthoringApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates with an Idempotency-Key', () => {
    api
      .create({ title: 'K8s', categorySlug: 'ci-cd', level: 'BEGINNER', language: 'en' }, 'key-1')
      .subscribe();

    const req = http.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
  });

  it('sends expectedVersion with every content write', () => {
    api
      .updateDetails('c1', 7, {
        title: 'T',
        subtitle: null,
        description: 'D',
        categorySlug: 'ci-cd',
        level: 'BEGINNER',
        language: 'en',
      })
      .subscribe();
    api.confirmPrice('c1', 8, 0).subscribe();
    api.undo('c1', 9).subscribe();

    expect(http.expectOne(`${base}/c1/details`).request.body).toEqual(
      expect.objectContaining({ expectedVersion: 7, title: 'T' }),
    );
    expect(http.expectOne(`${base}/c1/pricing`).request.body).toEqual({
      expectedVersion: 8,
      priceMinor: 0,
    });
    expect(http.expectOne(`${base}/c1/curriculum/undo`).request.body).toEqual({
      expectedVersion: 9,
    });
  });

  it('posts a curriculum command as a discriminated union', () => {
    api.apply('c1', 3, { kind: 'ADD_SECTION', title: 'Intro' }).subscribe();

    expect(http.expectOne(`${base}/c1/curriculum`).request.body).toEqual({
      expectedVersion: 3,
      command: { kind: 'ADD_SECTION', title: 'Intro' },
    });
  });

  it('posts a lifecycle action', () => {
    api.transition('c1', 'submit').subscribe();
    expect(http.expectOne(`${base}/c1/submit`).request.method).toBe('POST');
  });
});
