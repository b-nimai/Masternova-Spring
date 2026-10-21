# 10 — A typeahead that can't show a stale answer, reviews paged by hand, and a thread rendered flat

> **One-liner:** suggest course titles while someone types without ever letting a slow, old
> answer overwrite a newer one; page reviews with an opaque cursor; render a reply tree that the
> server already flattened; and post to a thread exactly once, even on a retry.

**Last updated:** 2026-10-21 · **Roadmap:** 11.5
**Code:**
- `core/api/search-api.ts`, `core/api/engagement-api.ts`: interfaces mirroring the DTOs by name;
- `shared/search-box/`: `SearchBox`, the toolbar's typeahead (`MatAutocomplete`);
- `features/search/search-results/`: `/search?q=&mode=`;
- `features/catalog/course-reviews/`: the course page's reviews;
- `features/learn/lecture-qa/`: the lecture's Q&A under the player.

## 1. The typeahead pipeline ⭐⭐⭐

```ts
toSignal(
  this.query.valueChanges.pipe(
    debounceTime(200),                // wait for a pause in typing
    map((text) => text.trim()),
    distinctUntilChanged(),           // "go " → "go": no second call
    switchMap((text) =>               // ⭐ a new text UNSUBSCRIBES from the old request
      text.length < 2
        ? of([])
        : this.api.suggest(text).pipe(catchError(() => of([]))),
    ),
  ),
  { initialValue: [] },
);
```

- **Why `switchMap`.** Typing "kub" then "kubernetes" sends two requests. If the first is slower,
  `mergeMap` would show its answer *last*, over the right one. `switchMap` unsubscribes from the
  previous inner Observable, and `HttpClient` aborts the request. The spec proves it with
  `HttpTestingController`: `first.cancelled === true`.
- **Why the `catchError` is inside.** An error that reaches the outer stream completes it, and the
  box would never suggest again. Inside the `switchMap`, a failed request is one empty list.
- **Why RxJS and not a signal `effect`.** The problem is about *time* (debounce) and
  *cancellation*. Signals have neither. `toSignal` is the boundary: the template reads a signal.
- The test drives time with Vitest's fake timers (`vi.advanceTimersByTime(199)` → no request;
  `+1` → one).

## 2. The page whose state is the URL ⭐⭐

`/search?q=kubernetes&mode=KEYWORD`: `q` and `mode` are query params bound to inputs
(`withComponentInputBinding`), and an `rxResource` keyed on both re-runs, cancelling the previous
request, when either changes. The mode toggle doesn't set state: it **navigates**
(`queryParamsHandling: 'merge'`), so Back undoes it and a link shares it. An unknown `?mode=`
falls back to the default: anyone can edit a URL.

`degraded: true` is **not an error**. The server answered from keywords because the embedding
model was unavailable (ADR-0024). The page says so in one line and shows the results.

## 3. Reviews: accumulated pages and a server-decided form ⭐⭐

The list is plain signals: `reviews` and `nextCursor`. "Show more" appends the next keyset page.
Note 04 did the same with a `scan` pipeline for a virtual scroll; here a button is enough. A
write (or delete) **resets to page one**: the learner's own review is now the newest, and the
cursor in hand describes a list that no longer exists.

Who may review (enrolled, not the course's teacher) is the **server's** rule. The form appears
for enrolled viewers (the course page already has their progress); a 403 `OWN_COURSE` becomes a
sentence, branching on the problem's `reason`, never on `detail`. "No review yet" is a 404 from
`GET /me/reviews/{course}`, mapped to `null` *inside* the resource's stream: a 404 is an answer.

## 4. A thread rendered flat ⭐⭐

The server returns the answers **in reading order** with a `depth` (its recursive CTE does the
tree walk, engagement LLD §5). So the template is one `@for` with
`[style.margin-left.rem]="(a.depth - 1) * 1.5"`. No recursive component, no client-side tree
building, and the reply button disappears at depth 3, the server's cap.

## 5. Posting exactly once ⭐⭐⭐

Asking and answering are creates with no version, so they carry an `Idempotency-Key` (API
conventions §4). **When to make a new key** is the whole lesson (note 05 met it on the course
wizard):

| What happened | Next attempt's key |
|---|---|
| network error / timeout (the server may have posted it) | **the same**: a retry replays the stored 201, one row |
| success | new (it's a new post) |
| 4xx (refused: too deep, not enrolled) | new: the corrected post is a different request, and the same key with a different body is 422 `IDEMPOTENCY_KEY_REUSED` |

The spec fails the first `POST` with a `ProgressEvent` and checks that the retry sends the same
key.

## 6. Pitfalls met

Found by the phase's code review, each with a spec now:

- **A picked option is an OBJECT in the control.** `MatAutocompleteTrigger` writes the option's
  value (`{slug, title}`) into the `FormControl`, so `map((text) => text.trim())` threw 200 ms
  after a pick, the stream ended, and the box never suggested again. The control is typed
  `FormControl<string | SuggestionResponse>` and a `filter(typeof === 'string')` comes first.
- **`value()` throws on a resource in error.** `summary.value()` in a `computed` broke the whole
  reviews block when the summary failed. Read through `hasValue() ? value() : undefined`.
- **A stale page after a reset.** "Show more" in flight, then a save resets the list: the late
  page was appended to the new list (duplicate `track` keys). The list keeps its one
  `Subscription` and unsubscribes when it starts over.
- **Q&A inside the player's `@switch`.** Shown only while `playing`, so article lectures and
  playback failures had none, and an error destroyed a question being typed. It sits outside.
- **`(ngSubmit)` on a bare `<form>` does nothing.** `ngSubmit` belongs to `NgForm` (FormsModule)
  or `[formGroup]`; the search box has a lone `[formControl]`, so Enter did the browser's own
  submit and reloaded the page. Unit specs never submitted; the e2e test caught it. It binds
  `(submit)` and calls `preventDefault()`, and a spec checks `defaultPrevented`.
- **Mutation-checking a spec:** after reverting a mutated file, `touch` it. The test builder
  served the mutated transpile once more.

- **`whenStable()` hangs while a resource is loading.** A loading `rxResource` (and an in-flight
  `HttpClient` call) is a *pending task*, so the fixture isn't stable until the test flushes the
  request. Before a flush: `TestBed.tick()`. After it: `await fixture.whenStable()` (the resource's
  value lands asynchronously).
- **`resource.reload()` fires on the next tick,** not synchronously: tick before expecting the
  request.
- **`data-testid` collisions across components.** The access CTA already had `review` (its
  "Review" button); the review items are `review-item`.
- **The initial bundle.** The toolbar's search box brought `MatAutocomplete`, forms and the form
  field into the first load: +134 kB. `@defer (on idle)` around it moved that into a lazy chunk:
  the initial bundle is +2.4 kB over Phase 10 (690.8 kB; the 500 kB budget was already exceeded
  before this phase).

## 7. Deviations from the StudyNotion Figma

The Figma couldn't be opened from this session (as in notes 07 and 09), so these screens follow
the existing pages and Material defaults:

- **Reviews** follow the Figma's course page "Reviews from other learners" block as remembered:
  average + stars + count, per-star bars, then the list. Added: the learner's own review form
  above the list (the Figma has none: StudyNotion rates on completion in a modal).
- **The search box** is in the toolbar beside the brand (the Figma's navbar has a search icon
  only); on phones it moves to the top of the drawer.
- **Q&A** has no frame in the Figma; it sits under the player as a list that expands into its
  thread.

Revisit with the Figma open before Phase D5's polish.

## 8. 30-second recall

Typeahead: `debounceTime → distinctUntilChanged → switchMap(request.pipe(catchError(of([]))))
→ toSignal`. `switchMap` cancels the stale request, and the inner `catchError` keeps the stream
alive. The results page's state is the URL. Reviews page by hand with an opaque cursor and reset
after a write. The server decides who may review. A thread arrives flat in reading order, so one
loop indented by depth renders it. An Idempotency-Key is kept for a retry after a network error
and renewed after a success or a refusal.
