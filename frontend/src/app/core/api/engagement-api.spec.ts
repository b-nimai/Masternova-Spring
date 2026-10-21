import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EngagementApi } from './engagement-api';

describe('EngagementApi', () => {
  let api: EngagementApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(EngagementApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pages reviews with the cursor it was given', () => {
    api.reviews('c1', 'next-1').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/courses/c1/reviews');
    expect(req.request.params.get('cursor')).toBe('next-1');
    expect(req.request.params.get('limit')).toBe('10');
  });

  it('writes a review with a PUT on the pair and no key', () => {
    api.writeReview('c1', { rating: 4, body: 'Good' }).subscribe();
    const req = http.expectOne('/api/v1/me/reviews/c1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.has('Idempotency-Key')).toBe(false);
    expect(req.request.body).toEqual({ rating: 4, body: 'Good' });
  });

  it('posts questions and answers with an Idempotency-Key', () => {
    api.ask('l1', { title: 'Why?', body: '' }, 'k1').subscribe();
    api.answer('q1', { parentAnswerId: 'a1', body: 'Because' }, 'k2').subscribe();
    const ask = http.expectOne('/api/v1/lectures/l1/questions');
    const answer = http.expectOne('/api/v1/questions/q1/answers');
    expect(ask.request.headers.get('Idempotency-Key')).toBe('k1');
    expect(answer.request.headers.get('Idempotency-Key')).toBe('k2');
    expect(answer.request.body).toEqual({ parentAnswerId: 'a1', body: 'Because' });
  });
});
