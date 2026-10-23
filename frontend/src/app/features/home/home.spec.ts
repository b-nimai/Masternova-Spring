import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CourseSummary } from '../../core/api/catalog-api';
import { AuthApi, Role, TokenResponse } from '../../core/auth/auth-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Home } from './home';

describe('Home', () => {
  let http: HttpTestingController;
  const course = (id: string, ratingCount: number): CourseSummary =>
    ({
      id,
      slug: `course-${id}`,
      title: `Course ${id}`,
      subtitle: null,
      level: 'BEGINNER',
      ratingCount,
      lectureCount: 6,
      instructorName: 'Asha',
    }) as CourseSummary;
  const tokens = (roles: Role[]): TokenResponse => ({
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 'u1', email: 'a@x.dev', displayName: 'Ada', roles, emailVerified: true },
  });
  let roles: Role[] = ['LEARNER'];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthApi, useValue: { login: () => of(tokens(roles)) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  /**
   * ⭐ `TestBed.tick()`, not `whenStable()`, while a resource's request is unanswered: whenStable
   * waits for pending resources, which wait for the flush below.
   */
  async function render(courses: CourseSummary[]) {
    const fixture = TestBed.createComponent(Home);
    TestBed.tick();
    http.expectOne('/api/v1/categories').flush([{ slug: 'web', name: 'Web', children: [] }]);
    http
      .expectOne((r) => r.url === '/api/v1/courses' && r.params.get('sort') === 'HIGHEST_RATED')
      .flush({ items: courses, nextCursor: null });
    await new Promise((resolve) => setTimeout(resolve)); // the resource settles after the flush
    TestBed.tick();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the three best-rated courses and the latest reviews of the first', async () => {
    const { fixture, el } = await render([course('a', 3), course('b', 1), course('c', 0)]);

    const titles = Array.from(el.querySelectorAll('[data-testid="top-courses"] h3')).map((h) =>
      h.textContent?.trim(),
    );
    expect(titles).toEqual(['Course a', 'Course b', 'Course c']);

    // ⭐ the chained resource: its request goes out only once the first course is known
    http
      .expectOne((r) => r.url === '/api/v1/courses/a/reviews')
      .flush({
        items: [{ id: 'r1', rating: 5, body: 'Great.', authorName: 'Ravi' }],
        nextCursor: null,
      });
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="home-reviews"]')?.textContent).toContain('Great.');
  });

  it('asks for no reviews when the best course has none, and hides the section', async () => {
    const { el } = await render([course('a', 0)]);

    http.expectNone((r) => r.url.endsWith('/reviews'));
    expect(el.querySelector('[data-testid="home-reviews"]')).toBeNull();
  });

  it('still renders when the courses and categories requests fail', async () => {
    const fixture = TestBed.createComponent(Home);
    TestBed.tick();
    http.expectOne('/api/v1/categories').flush(null, { status: 500, statusText: 'Down' });
    http
      .expectOne((r) => r.url === '/api/v1/courses')
      .flush(null, { status: 500, statusText: 'Down' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toContain('Empower Your Future');
    expect(el.querySelector('.stats strong')?.textContent).toBe('0');
    expect(el.querySelector('[data-testid="home-reviews"]')).toBeNull();
  });

  const teachHref = (el: HTMLElement) =>
    el.querySelector<HTMLAnchorElement>('.pill')?.getAttribute('href') ?? null;
  const signIn = (as: Role[]) => {
    roles = as;
    TestBed.inject(AuthStore).login('a@x.dev', 'pw').subscribe();
  };

  it('sends a visitor to sign up to teach', async () => {
    const { el } = await render([]);
    expect(teachHref(el)).toBe('/signup?as=instructor');
  });

  it('sends an instructor to their courses', async () => {
    signIn(['LEARNER', 'INSTRUCTOR']);
    const { el } = await render([]);
    expect(teachHref(el)).toBe('/instructor');
  });

  it('sends a learner to apply from their account (an admin grants the role)', async () => {
    signIn(['LEARNER']);
    const { el } = await render([]);
    expect(teachHref(el)).toBe('/account#teach');
  });
});
