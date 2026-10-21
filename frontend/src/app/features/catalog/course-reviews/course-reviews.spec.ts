import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseReviews } from './course-reviews';

describe('CourseReviews', () => {
  let fixture: ComponentFixture<CourseReviews>;
  let http: HttpTestingController;
  const el = (id: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  const review = (id: string, rating: number) => ({
    id,
    rating,
    body: `review ${id}`,
    authorName: 'Lena',
    createdAt: '2026-10-21T10:00:00Z',
    updatedAt: '2026-10-21T10:00:00Z',
    edited: false,
  });

  async function render(canReview: boolean): Promise<void> {
    fixture.componentRef.setInput('courseId', 'c1');
    fixture.componentRef.setInput('canReview', canReview);
    fixture.detectChanges();
    TestBed.tick(); // a loading resource is a pending task: whenStable would wait for the flush
    http
      .expectOne('/api/v1/courses/c1/reviews/summary')
      .flush({ average: 4.5, count: 2, stars: [0, 0, 0, 1, 1] });
    http
      .expectOne((r) => r.url === '/api/v1/courses/c1/reviews' && !r.params.has('cursor'))
      .flush({ items: [review('r1', 5)], nextCursor: 'next' });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CourseReviews],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(CourseReviews);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the average and appends the next page on "Show more"', async () => {
    await render(false);
    fixture.detectChanges();
    expect(el('average')?.textContent).toContain('4.5');
    expect(el('my-review')).toBeNull(); // not enrolled: no form, and no /me call

    el('more-reviews')?.click();
    http
      .expectOne((r) => r.params.get('cursor') === 'next')
      .flush({ items: [review('r2', 4)], nextCursor: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="review-item"]').length).toBe(2);
    expect(el('more-reviews')).toBeNull();
  });

  it('lets an enrolled learner post a review, then starts the list over', async () => {
    await render(true);
    http
      .expectOne('/api/v1/me/reviews/c1')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();

    el('star-4')?.click();
    fixture.detectChanges();
    el('save-review')?.click();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body).toEqual({ rating: 4, body: '' });
    put.flush(review('mine', 4));
    TestBed.tick(); // summary.reload() runs on the next tick

    http
      .expectOne('/api/v1/courses/c1/reviews/summary')
      .flush({ average: 4.33, count: 3, stars: [0, 0, 0, 2, 1] });
    http
      .expectOne((r) => r.url === '/api/v1/courses/c1/reviews')
      .flush({ items: [], nextCursor: null });
  });

  it('drops a "Show more" page that lands after the list was started over', async () => {
    await render(true);
    http
      .expectOne('/api/v1/me/reviews/c1')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();

    el('more-reviews')?.click();
    const stale = http.expectOne((r) => r.params.get('cursor') === 'next');
    el('star-3')?.click();
    fixture.detectChanges();
    el('save-review')?.click();
    http.expectOne((r) => r.method === 'PUT').flush(review('mine', 3));
    TestBed.tick();

    expect(stale.cancelled).toBe(true); // ⭐ its page belonged to the old list
    http
      .expectOne('/api/v1/courses/c1/reviews/summary')
      .flush({ average: 4, count: 3, stars: [0, 0, 1, 1, 1] });
    http
      .expectOne((r) => r.url === '/api/v1/courses/c1/reviews' && !r.params.has('cursor'))
      .flush({ items: [review('mine', 3), review('r1', 5)], nextCursor: null });
  });

  it('still lists reviews when the summary fails', async () => {
    fixture.componentRef.setInput('courseId', 'c1');
    fixture.detectChanges();
    TestBed.tick();
    http
      .expectOne('/api/v1/courses/c1/reviews/summary')
      .flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    http
      .expectOne((r) => r.url === '/api/v1/courses/c1/reviews')
      .flush({ items: [review('r1', 5)], nextCursor: null });
    await fixture.whenStable();
    fixture.detectChanges(); // used to throw: value() of a resource in error

    expect(el('average')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="review-item"]').length).toBe(1);
  });

  it('explains a refusal instead of failing', async () => {
    await render(true);
    http
      .expectOne('/api/v1/me/reviews/c1')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();

    el('star-5')?.click();
    fixture.detectChanges();
    el('save-review')?.click();
    http
      .expectOne((r) => r.method === 'PUT')
      .flush(
        { status: 403, code: 'FORBIDDEN', reason: 'OWN_COURSE' },
        { status: 403, statusText: 'Forbidden' },
      );
    fixture.detectChanges();

    expect(el('review-error')?.textContent).toContain('You teach this course');
  });
});
