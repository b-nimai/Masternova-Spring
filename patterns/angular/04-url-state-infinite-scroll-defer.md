# 04 — URL as state, infinite scroll with a cursor, and `@defer`

> **One-liner:** put a list's **filters in the URL** (bound straight to component inputs), page it
> with the API's **opaque cursor** through one RxJS pipeline (`switchMap` per query, `exhaustMap`
> per page, `scan` to accumulate), render it in a **CDK virtual scroll** viewport, and **defer**
> the heavy, below-the-fold parts of a page until they scroll into view.

**Roadmap:** task 5.8 · **Last updated:** 2026-10-02 · **Prev:** [03 — Optimistic UI](03-optimistic-ui-and-server-state.md)
**Code:**
- [`core/api/catalog-api.ts`](../../frontend/src/app/core/api/catalog-api.ts): interfaces mirror the backend DTOs by name; `HttpParams` with repeated params.
- [`features/catalog/catalog.ts`](../../frontend/src/app/features/catalog/catalog.ts) (`/courses`): URL state, the paging pipeline, virtual scroll.
- [`features/catalog/course-detail/`](../../frontend/src/app/features/catalog/course-detail/) (`/courses/:slug`): route param → input, `rxResource`, `@defer`.
- [`features/catalog/curriculum/`](../../frontend/src/app/features/catalog/curriculum/): the deferred chunk. [`shared/money-pipe.ts`](../../frontend/src/app/shared/money-pipe.ts), [`shared/duration-pipe.ts`](../../frontend/src/app/shared/duration-pipe.ts).
**Tests:** the `catalog/**/*.spec.ts` specs (Vitest) and [`e2e/tests/catalog.spec.ts`](../../e2e/tests/catalog.spec.ts) (Playwright: reload, Back, real scrolling, real `@defer`).
**Run:** `make up && make api && make seed && make web` → http://localhost:4200/courses

| # | Section | Priority |
|---|---|---|
| 1 | [The URL is the state](#1-the-url-is-the-state-) | ⭐⭐⭐ |
| 2 | [Query params → inputs](#2-query-params--inputs-) | ⭐⭐⭐ |
| 3 | [Controls write the URL](#3-controls-write-the-url-) | ⭐⭐⭐ |
| 4 | [Cursor paging as one RxJS pipeline](#4-cursor-paging-as-one-rxjs-pipeline-) | ⭐⭐⭐ |
| 5 | [Virtual scroll](#5-virtual-scroll-) | ⭐⭐ |
| 6 | [`rxResource` for "load X for this param"](#6-rxresource-for-load-x-for-this-param-) | ⭐⭐ |
| 7 | [`@defer`](#7-defer-) | ⭐⭐ |
| 8 | [Money and pipes](#8-money-and-pipes-) | ⭐⭐ |
| 9 | [Testing it](#9-testing-it-) | ⭐⭐⭐ |
| 10 | [Common mistakes](#10-common-mistakes-) | ⭐⭐⭐ |
| 11 | [Interview Q&A](#11-interview-qa) | ⭐⭐⭐ |
| 12 | [30-second recall](#12-30-second-recall) | ⭐⭐⭐ |

---

## 1. The URL is the state ⭐⭐⭐

Where should "free beginner DevOps courses, cheapest first" live? Not in a component field, not
in a service: **in the URL.**

```text
/courses?category=devops-cloud&level=BEGINNER&price=FREE&sort=PRICE_LOW
```

| If the filters live in… | Reload | Share a link | Back button | Open in new tab |
|---|---|---|---|---|
| a component signal | lost | lost | leaves the page | lost |
| a singleton service | lost | lost | leaves the page | lost |
| ⭐ the URL | kept | kept | undoes the last filter | kept |

The rule that keeps it simple: **one direction each way.** Controls only *write* the URL; the
component only *reads* the URL. There's no second copy of a filter to get out of sync.

What does **not** go in the URL: the cursor and the loaded pages. Scroll position isn't a filter,
and a bookmarked cursor would be stale (a 400 the next day).

## 2. Query params → inputs ⭐⭐⭐

`provideRouter(routes, withComponentInputBinding())` (already in `app.config.ts`) binds route
params, query params and route data to **inputs of the routed component** with the same name:

```ts
export class Catalog {
  readonly q = input<string>();
  readonly category = input<string>();
  readonly level = input<string | string[]>();   // ⭐ ?level=A → 'A'; ?level=A&level=B → ['A','B']
  readonly price = input<string>();
  readonly sort = input<string>();

  /** The URL's filters, validated: a hand-edited ?level=EXPERT is ignored, not sent. */
  protected readonly query = computed<CourseQuery>(() => ({ ... }));
}
```

- No `ActivatedRoute`, no `queryParamMap.subscribe`, no unsubscribing.
- The inputs are **signals**: `query` is a `computed` over them, so it updates whenever the URL does.
- **Validate** what comes from the URL. Anyone can type `?sort=BEST`. The component narrows the
  strings to the API's enums and drops the rest (unit test: "drops URL values the API would reject").
- The same mechanism binds the **route param** `:slug` to `CourseDetail.slug`.

## 3. Controls write the URL ⭐⭐⭐

```ts
private setParams(params: Params): void {
  void this.router.navigate([], {
    relativeTo: this.route,
    queryParams: params,               // e.g. { price: 'FREE' } — null REMOVES a param
    queryParamsHandling: 'merge',      // ⭐ keep the other filters
  });
}

protected setSort(sort: CourseSort): void {
  this.setParams({ sort: sort === 'NEWEST' ? null : sort });   // defaults stay out of the URL
}
```

The search box is the one control that needs care: typing shouldn't create ten history entries.

```ts
this.searchControl.valueChanges
  .pipe(map((t) => t.trim()), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
  .subscribe((text) => this.setParams({ q: text || null }));

// …and the URL writes the box (Back button, shared link), WITHOUT echoing a navigation back
effect(() => {
  const fromUrl = this.q() ?? '';
  if (fromUrl.trim() !== this.searchControl.value.trim()) {
    this.searchControl.setValue(fromUrl, { emitEvent: false });   // ⭐ emitEvent: false breaks the loop
  }
});
```

## 4. Cursor paging as one RxJS pipeline ⭐⭐⭐

The API returns `{ items, nextCursor }` (API conventions §2). The page needs: page 1 when the
query changes; the next page when the user nears the end; never a stale page from an old query;
never two requests for the same page.

```ts
// per QUERY: switchMap — a new query cancels the old one's in-flight request
toObservable(this.query)
  .pipe(
    distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    switchMap((query) => this.pages(query)),
    takeUntilDestroyed(),
  )
  .subscribe((state) => this.list.set(state));

// per PAGE of one query: exhaustMap — ignore "load more" while a page is loading
private pages(query: CourseQuery): Observable<ListState> {
  let cursor: string | null = null;            // page 1
  let done = false;
  return this.loadMore$.pipe(
    startWith(undefined),                      // load page 1 immediately
    filter(() => !done),
    exhaustMap(() =>
      this.api.browse(query, cursor).pipe(
        map((page): PageEvent => {
          cursor = page.nextCursor;            // ⭐ the opaque cursor, passed back untouched
          done = page.nextCursor === null;
          return { kind: 'page', items: page.items, nextCursor: page.nextCursor };
        }),
        catchError(() => of<PageEvent>({ kind: 'error' })),   // a retry calls loadMore() again
        startWith<PageEvent>({ kind: 'loading' }),
      ),
    ),
    scan(reduce, INITIAL),                     // fold the events into { items, nextCursor, loading, error }
  );
}
```

| Operator | Here it means | What breaks with the wrong one |
|---|---|---|
| `switchMap` (per query) | a new filter **cancels** the old request | `mergeMap`: a slow page of the *old* filters lands in the *new* list |
| `exhaustMap` (per page) | scroll storms → **one** request; the rest ignored | `mergeMap`: five requests with the same cursor → duplicate rows. `concatMap`: queued pages load after the user stopped scrolling |
| `scan` | accumulate pages into one list | — |
| `startWith` | page 1 without waiting for a scroll | — |

⭐ The unit test **"starts over when a filter changes, cancelling the old in-flight page"** asserts
`stale.cancelled === true`: that one line is the whole reason `switchMap` is there.

## 5. Virtual scroll ⭐⭐

```html
<cdk-virtual-scroll-viewport class="results" [itemSize]="cardHeight" (scrolledIndexChange)="onScrolled()">
  <app-course-card *cdkVirtualFor="let course of state.items; trackBy: trackById" [course]="course"
                   [style.height.px]="cardHeight" />
</cdk-virtual-scroll-viewport>
```

- Only the rows in view (plus a buffer) exist in the DOM. After 40 loaded courses the e2e test
  counts **fewer than 20** `app-course-card` elements.
- The fixed-size strategy needs every row exactly `itemSize` px tall (the card sets its height).
  Variable heights need `autosize` from `@angular/cdk-experimental` or a different technique.
- The viewport needs a **fixed height** (CSS), otherwise it grows to fit everything and
  virtualises nothing.
- `*cdkVirtualFor` is a structural directive: the built-in `@for` can't virtualise.
- Load the next page when the rendered range nears the end:

```ts
protected onScrolled(): void {
  const viewport = this.viewport();
  if (viewport && viewport.getRenderedRange().end >= this.list().items.length - 3) {
    this.loadMore();
  }
}
```

A side effect worth knowing: if page 1 doesn't fill the viewport, the rendered range already
reaches the end, so page 2 loads straight away. That's what you want on a tall screen. It's also
why the unit tests flush **full 20-item pages**: a 2-item page in jsdom (a 0-px viewport)
correctly triggers an immediate second request.

**Accessibility:** a visible "Load more" button stays below the list. Keyboard and screen-reader
users get a way to the next page that doesn't depend on scrolling, and tests get a stable trigger.

## 6. `rxResource` for "load X for this param" ⭐⭐

```ts
readonly slug = input.required<string>();          // /courses/:slug

protected readonly course = rxResource({
  params: () => this.slug(),                        // re-runs when the slug changes…
  stream: ({ params: slug }) => this.api.course(slug), // …and cancels the previous request
});
```

The template reads signals: `course.isLoading()`, `course.error()`, `course.value()`,
`course.reload()`. A 404 is turned into a "not found" state (`notFound = computed(...)`),
distinct from "the server is down".

Use a resource for **one value driven by params**. For an accumulating list driven by events
(scroll), the RxJS pipeline in §4 is still the better tool.

## 7. `@defer` ⭐⭐

```html
@defer (on viewport) {
  <app-curriculum [sections]="c.sections" />
} @placeholder {
  <p>{{ c.sections.length }} sections · {{ c.lectureCount }} lectures</p>
} @loading (minimum 200ms) {
  <mat-spinner diameter="28" />
}
```

- Every standalone component, directive and pipe used **only** inside the block is split into its
  own chunk. The production build shows it: `curriculum` **23.5 kB** lazy, `course-detail` 4.3 kB.
- `on viewport` uses an `IntersectionObserver` on the placeholder, so the placeholder needs a
  real box. Other triggers: `on idle` (the default), `on interaction`, `on hover`,
  `on timer(2s)`, `when someCondition`, plus `prefetch on idle`.
- `@loading (minimum 200ms)` avoids a spinner that flashes for 20 ms.
- It defers **code and rendering**, not data: the curriculum's data came with the course. To defer
  data too, fetch inside the deferred component.

## 8. Money and pipes ⭐⭐

The API sends `{ priceMinor: 149900, currency: "INR" }`. The **client** formats it:

```ts
transform(amountMinor: number, currency: string): string {
  if (amountMinor === 0) return 'Free';
  const format = new Intl.NumberFormat(this.locale, { style: 'currency', currency });
  const digits = format.resolvedOptions().maximumFractionDigits ?? 2;   // ⭐ INR 2, JPY 0
  return format.format(amountMinor / 10 ** digits);
}
```

Never hard-code `/ 100`: ¥500 would render as ¥5.00. Pure pipes (the default) re-run only when
their input changes, so formatting in the template is cheap.

## 9. Testing it ⭐⭐⭐

| What | How |
|---|---|
| URL → request | `fixture.componentRef.setInput('level', ['BEGINNER', 'ADVANCED'])` (exactly what the router does), then assert the `HttpTestingController` request's params |
| control → URL | `vi.spyOn(router, 'navigate')`, click the Material toggle's inner `button`, assert `queryParams` + `queryParamsHandling: 'merge'` |
| debounce | `vi.useFakeTimers()` **after** the page is stable (RxJS schedules the timer when a value arrives), type three values 100 ms apart, `advanceTimersByTime(300)`, assert **one** navigation |
| cancellation | after a filter change, `stale.cancelled === true` |
| `rxResource` | `TestBed.tick()` after `setInput`, **not** `await whenStable()`: a loading resource is a pending task, so the fixture isn't stable until its request is flushed (the test would time out) |
| `@defer` | `deferBlockBehavior: DeferBlockBehavior.Manual`, then `(await fixture.getDeferBlocks())[0].render(DeferBlockState.Complete)` |
| real browser | Playwright: reload and Back keep the filters; scrolling the viewport fires a request with `cursor=`; the curriculum appears after `scrollIntoViewIfNeeded()` |

Shared test data lives in `src/testing/catalog-fixtures.ts`, a plain module. Exporting a fixture
from a `*.spec.ts` file and importing it elsewhere re-registers that spec's tests in the importing
file.

## 10. Common mistakes ⭐⭐⭐

| Mistake | Symptom | Fix |
|---|---|---|
| filters in a component field | reload / share / Back lose them | the URL is the state |
| `navigate` without `queryParamsHandling: 'merge'` | setting one filter clears the others | merge, and `null` to remove |
| URL ↔ form sync without `emitEvent: false` | navigation loops, double history entries | break the loop on the URL → form side |
| `mergeMap` for pages | duplicate rows, old-filter rows in the new list | `switchMap` per query, `exhaustMap` per page |
| putting the cursor in the URL | stale bookmarks → 400 | the cursor is in-memory paging state |
| parsing or building cursors in the client | breaks when the server changes the format | pass `nextCursor` back untouched |
| virtual scroll without a fixed height or with variable rows | no virtualisation, or jumpy scrolling | fixed viewport height + fixed `itemSize` |
| `whenStable()` while an `rxResource` loads | the test times out | `TestBed.tick()`, flush, then `whenStable()` |
| `/ 100` for money | wrong for zero-decimal currencies | Intl's fraction digits |
| a `data-testid` reused in two components | e2e matches the wrong element ("price" was both a filter and a card) | unique, scoped test ids |

## 11. Interview Q&A

- **Q:** Where do you keep a list page's filters, and why?
  **A:** In the URL query string, bound to the component's inputs with `withComponentInputBinding`.
  Reload, sharing and Back work for free, and there's one source of truth. Controls navigate with
  `queryParamsHandling: 'merge'`; the component only reads.
- **Q:** How do you implement infinite scroll against a cursor API?
  **A:** One pipeline: `switchMap` over the query (a new filter cancels everything from the old
  one), and inside it a "load more" stream through `exhaustMap` (one page request at a time, each
  using the previous page's `nextCursor`), folded with `scan`. Stop when `nextCursor` is null.
- **Q:** `switchMap` vs `mergeMap` vs `concatMap` vs `exhaustMap`?
  **A:** switch cancels the previous inner (latest wins: searches, filters); merge runs all in
  parallel (independent work); concat queues (order matters: writes); exhaust ignores new ones
  while busy (double clicks, scroll storms).
- **Q:** What does virtual scrolling buy, and what does it cost?
  **A:** Only visible rows are in the DOM, so 10,000 loaded items cost as much to render as 10.
  Costs: a fixed-height container, known row heights, and Ctrl+F only finds rendered rows.
- **Q:** What does `@defer` do?
  **A:** Splits the components used only inside the block into a lazy chunk and renders the block
  when a trigger fires (viewport, idle, interaction, timer, condition), showing `@placeholder` /
  `@loading` meanwhile. Good for heavy, below-the-fold UI.
- **Q:** When would you use `rxResource` / `httpResource`?
  **A:** When one value depends on reactive params (a course for a slug): it re-fetches on change,
  cancels the old request, and exposes loading/error/value as signals.

## 12. 30-second recall

- **URL = state:** query params → inputs (`withComponentInputBinding`); controls `navigate(...,
  {queryParamsHandling: 'merge'})`; `null` removes; validate URL values; no cursor in the URL.
- **Paging:** `switchMap(query)` → `loadMore$.pipe(startWith, exhaustMap(browse(query, cursor)),
  scan)`; stop at `nextCursor === null`; never parse the cursor.
- **Virtual scroll:** fixed viewport height + fixed `itemSize`; `*cdkVirtualFor`; load more near
  the end of the rendered range; keep a "Load more" button for a11y.
- **Resource:** `rxResource({params, stream})` for "X for this param"; `TestBed.tick()` in tests.
- **`@defer (on viewport)`:** a separate chunk (curriculum 23.5 kB); placeholder needs a box;
  test with `DeferBlockBehavior.Manual`.
- **Money:** minor units → Intl currency digits; zero → "Free".
