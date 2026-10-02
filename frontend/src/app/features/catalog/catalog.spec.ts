import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CategoryResponse } from '../../core/api/catalog-api';
import { page } from '../../../testing/catalog-fixtures';
import { query, text } from '../../../testing/dom';
import { Catalog } from './catalog';

const CATEGORIES: CategoryResponse[] = [
  {
    slug: 'devops-cloud',
    name: 'DevOps & Cloud',
    children: [{ slug: 'ci-cd', name: 'CI/CD', children: [] }],
  },
];

describe('Catalog', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Catalog],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  /** The page with these query params bound as inputs (what withComponentInputBinding does). */
  async function open(params: Record<string, unknown> = {}): Promise<ComponentFixture<Catalog>> {
    const fixture = TestBed.createComponent(Catalog);
    for (const [name, value] of Object.entries(params)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    http.expectOne('/api/v1/categories').flush(CATEGORIES);
    return fixture;
  }

  function browse(): TestRequest {
    return http.expectOne((r) => r.url === '/api/v1/courses');
  }

  async function settle(fixture: ComponentFixture<Catalog>): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('reads every filter from the URL and loads the first page', async () => {
    const fixture = await open({
      q: 'k8s',
      category: 'devops-cloud',
      level: ['BEGINNER', 'ADVANCED'],
      price: 'FREE',
      sort: 'PRICE_LOW',
    });

    const req = browse();
    expect(req.request.params.get('q')).toBe('k8s');
    expect(req.request.params.get('category')).toBe('devops-cloud');
    expect(req.request.params.getAll('level')).toEqual(['BEGINNER', 'ADVANCED']);
    expect(req.request.params.get('price')).toBe('FREE');
    expect(req.request.params.get('sort')).toBe('PRICE_LOW');
    expect(req.request.params.has('cursor')).toBe(false);

    req.flush(page(1, 20, 'cursor-2'));
    await settle(fixture);
    expect(text(fixture, 'status')).toBe('20 courses so far');
    expect(query(fixture, '[data-testid="load-more"]')).toBeTruthy();
  });

  it('drops URL values the API would reject instead of sending them', async () => {
    await open({ level: 'EXPERT', sort: 'BEST', price: 'CHEAP' });

    const req = browse();
    expect(req.request.params.keys()).toEqual(['limit']);
    req.flush(page(1, 0, null));
  });

  it('loads the next page with the cursor and appends it', async () => {
    const fixture = await open();
    browse().flush(page(1, 20, 'cursor-2'));
    await settle(fixture);

    query<HTMLButtonElement>(fixture, '[data-testid="load-more"]').click();
    const next = browse();
    expect(next.request.params.get('cursor')).toBe('cursor-2'); // ⭐ the opaque cursor, untouched
    next.flush(page(21, 25, null));
    await settle(fixture);

    expect(text(fixture, 'status')).toBe('25 courses'); // the last page: no "so far"
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="load-more"]'),
    ).toBeNull();
  });

  it('starts over when a filter changes, cancelling the old in-flight page', async () => {
    const fixture = await open();
    browse().flush(page(1, 20, 'cursor-2'));
    await settle(fixture);
    query<HTMLButtonElement>(fixture, '[data-testid="load-more"]').click();
    const stale = browse();

    fixture.componentRef.setInput('sort', 'PRICE_LOW');
    await fixture.whenStable();

    // ⭐ switchMap: the old query's page-2 request is cancelled, so it can never be appended
    expect(stale.cancelled).toBe(true);
    const fresh = browse();
    expect(fresh.request.params.get('sort')).toBe('PRICE_LOW');
    expect(fresh.request.params.has('cursor')).toBe(false); // page 1 of the NEW query
    fresh.flush(page(90, 90, null));
    await settle(fixture);
    expect(text(fixture, 'status')).toBe('1 courses');
  });

  it('writes a chosen filter to the URL instead of keeping it locally', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = await open();
    browse().flush(page(1, 1, null));
    await settle(fixture);

    query<HTMLButtonElement>(fixture, '[data-testid="price-FREE"] button').click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { price: 'FREE' }, queryParamsHandling: 'merge' }),
    );
  });

  it('debounces the search box into one navigation', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = await open();
    browse().flush(page(1, 0, null));

    // ⭐ fake timers only NOW: RxJS schedules the debounce timer when a value arrives, so typing
    //    after this line is fully under the fake clock (and whenStable above wasn't)
    vi.useFakeTimers();
    try {
      const input = query<HTMLInputElement>(fixture, '[data-testid="search"]');
      for (const value of ['k', 'ku', 'kube']) {
        input.value = value;
        input.dispatchEvent(new Event('input'));
        vi.advanceTimersByTime(100); // typing faster than the 300 ms debounce
      }
      expect(navigate).not.toHaveBeenCalled();
      vi.advanceTimersByTime(300);

      expect(navigate).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledWith(
        [],
        expect.objectContaining({ queryParams: { q: 'kube' } }),
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows an error that can be retried', async () => {
    const fixture = await open();
    browse().flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    await settle(fixture);
    expect(text(fixture, 'error')).toContain("couldn't load courses");

    query<HTMLButtonElement>(fixture, '[data-testid="retry"]').click();
    browse().flush(page(1, 1, null));
    await settle(fixture);
    expect(text(fixture, 'status')).toBe('1 courses');
  });

  it('says so when nothing matches', async () => {
    const fixture = await open({ q: 'nothing' });
    browse().flush(page(1, 0, null));
    await settle(fixture);

    expect(text(fixture, 'status')).toBe('No courses match these filters.');
    expect(query(fixture, '[data-testid="clear"]')).toBeTruthy();
  });
});
