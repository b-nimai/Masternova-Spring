import { CdkVirtualScrollViewport, ScrollingModule } from '@angular/cdk/scrolling';
import { Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Params, Router } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  exhaustMap,
  filter,
  map,
  Observable,
  of,
  scan,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import {
  CatalogApi,
  COURSE_LEVELS,
  COURSE_SORTS,
  CourseLevel,
  CourseQuery,
  CourseSort,
  CourseSummary,
  PriceFilter,
} from '../../core/api/catalog-api';
import { LEVEL_LABELS, SORT_LABELS } from './catalog-labels';
import { CourseCard } from './course-card/course-card';

/** The list as the page shows it. */
interface ListState {
  items: CourseSummary[];
  /** null once the last page has arrived. */
  nextCursor: string | null;
  loading: boolean;
  error: boolean;
}

const INITIAL: ListState = { items: [], nextCursor: null, loading: true, error: false };

type PageEvent =
  | { kind: 'loading' }
  | { kind: 'page'; items: CourseSummary[]; nextCursor: string | null }
  | { kind: 'error' };

/** Card height in px — CdkVirtualScrollViewport's fixed-size strategy needs it exactly. */
export const CARD_HEIGHT = 148;

/**
 * /courses — the public catalog. Study note: patterns/angular/04-url-state-and-infinite-scroll.md.
 *
 * ⭐ THE URL IS THE STATE. Every filter lives in the query string (`?category=devops-cloud&
 *    level=BEGINNER`), bound to this component's inputs by `withComponentInputBinding()`. So a
 *    filtered list can be bookmarked, shared and reloaded, and Back undoes a filter. The component
 *    never stores a filter of its own: controls WRITE the URL; inputs READ it.
 *
 * ⭐ INFINITE SCROLL with the API's opaque cursor, inside a CDK virtual scroll viewport (only the
 *    rows on screen exist in the DOM, however many pages are loaded).
 */
@Component({
  imports: [
    ReactiveFormsModule,
    ScrollingModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    CourseCard,
  ],
  selector: 'app-catalog',
  styleUrl: './catalog.scss',
  templateUrl: './catalog.html',
})
export class Catalog {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // ⭐ query params → inputs (withComponentInputBinding). A repeated param (?level=A&level=B)
  //    arrives as an array, a single one as a string.
  readonly q = input<string>();
  readonly category = input<string>();
  readonly level = input<string | string[]>();
  readonly price = input<string>();
  readonly sort = input<string>();

  protected readonly levels = COURSE_LEVELS;
  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly sorts = COURSE_SORTS;
  protected readonly sortLabels = SORT_LABELS;
  protected readonly cardHeight = CARD_HEIGHT;

  /** The URL's filters, validated: a hand-edited `?level=EXPERT` is ignored, not sent. */
  protected readonly query = computed<CourseQuery>(() => {
    const levels = ([] as string[])
      .concat(this.level() ?? [])
      .filter((l): l is CourseLevel => (COURSE_LEVELS as readonly string[]).includes(l));
    return {
      q: this.q()?.trim() || undefined,
      category: this.category() || undefined,
      level: levels.length ? levels : undefined,
      price:
        this.price() === 'FREE' || this.price() === 'PAID'
          ? (this.price() as PriceFilter)
          : undefined,
      sort: (COURSE_SORTS as readonly string[]).includes(this.sort() ?? '')
        ? (this.sort() as CourseSort)
        : undefined,
    };
  });

  protected readonly categories = toSignal(this.api.categories(), { initialValue: [] });
  protected readonly searchControl = new FormControl('', { nonNullable: true });

  private readonly loadMore$ = new Subject<void>();
  protected readonly list = signal<ListState>(INITIAL);
  protected readonly hasFilters = computed(() =>
    Object.values(this.query()).some((v) => v !== undefined),
  );

  private readonly viewport = viewChild(CdkVirtualScrollViewport);

  constructor() {
    // ⭐ a new query (any filter changed) → start over from page 1. switchMap CANCELS the old
    //    query's in-flight request, so a slow page of the previous filters can never be appended
    //    to the new list.
    toObservable(this.query)
      .pipe(
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        switchMap((query) => this.pages(query)),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.list.set(state));

    // the search box writes the URL — debounced, so typing "kubernetes" is one navigation, not ten
    this.searchControl.valueChanges
      .pipe(
        map((text) => text.trim()),
        debounceTime(300),
        // ⭐ compare with the URL, not with the last value typed: distinctUntilChanged() would
        //    remember "kubernetes" across a "Clear filters" (which resets the box without an
        //    event), and typing "kubernetes" again would then be swallowed (found in review)
        filter((text) => text !== (this.q() ?? '').trim()),
        takeUntilDestroyed(),
      )
      .subscribe((text) => this.setParams({ q: text || null }));

    // …and the URL writes the search box (Back button, shared link), without echoing back
    effect(() => {
      const fromUrl = this.q() ?? '';
      if (fromUrl.trim() !== this.searchControl.value.trim()) {
        this.searchControl.setValue(fromUrl, { emitEvent: false });
      }
    });
  }

  /**
   * All pages of ONE query: the first page now, the next one each time `loadMore$` fires.
   *
   * ⭐ exhaustMap: while a page is loading, further "load more" signals (a scroll fires dozens)
   *    are IGNORED rather than queued — one request at a time, each with the cursor of the page
   *    before it. scan folds the page events into the list.
   */
  private pages(query: CourseQuery): Observable<ListState> {
    let cursor: string | null = null;
    let done = false;
    return this.loadMore$.pipe(
      startWith(undefined),
      filter(() => !done),
      exhaustMap(() =>
        this.api.browse(query, cursor).pipe(
          map((page): PageEvent => {
            cursor = page.nextCursor;
            done = page.nextCursor === null;
            return { kind: 'page', items: page.items, nextCursor: page.nextCursor };
          }),
          catchError(() => of<PageEvent>({ kind: 'error' })),
          startWith<PageEvent>({ kind: 'loading' }),
        ),
      ),
      scan((state: ListState, event: PageEvent): ListState => {
        switch (event.kind) {
          case 'loading':
            return { ...state, loading: true, error: false };
          case 'page':
            return {
              items: [...state.items, ...event.items], // ⭐ a new array: signals see the change
              nextCursor: event.nextCursor,
              loading: false,
              error: false,
            };
          case 'error':
            return { ...state, loading: false, error: true };
        }
      }, INITIAL),
    );
  }

  /** Called as rows scroll into view: near the end of what's loaded, ask for the next page. */
  protected onScrolled(): void {
    const viewport = this.viewport();
    if (viewport && viewport.getRenderedRange().end >= this.list().items.length - 3) {
      this.loadMore();
    }
  }

  protected loadMore(): void {
    if (this.list().nextCursor !== null || this.list().error) {
      this.loadMore$.next();
    }
  }

  // ------------------------------------------------------------------ controls → URL

  protected setCategory(category: string | null): void {
    this.setParams({ category });
  }

  protected setLevels(levels: CourseLevel[]): void {
    this.setParams({ level: levels.length ? levels : null });
  }

  protected setPrice(price: string): void {
    this.setParams({ price: price || null });
  }

  protected setSort(sort: CourseSort): void {
    this.setParams({ sort: sort === 'NEWEST' ? null : sort }); // the default stays out of the URL
  }

  protected clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  /**
   * ⭐ merge: only the changed param is touched; null REMOVES it. The cursor is never in the URL:
   * scroll position is not a filter, and a stale cursor in a bookmark would be a 400.
   */
  private setParams(params: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
    });
  }

  protected trackById(_: number, course: CourseSummary): string {
    return course.id;
  }
}
