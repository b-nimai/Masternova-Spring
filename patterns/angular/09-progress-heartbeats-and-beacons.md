# 09 — Heartbeats, a beacon on the way out, and a dashboard that is allowed to lag

> **One-liner:** tell the server where the learner is every 15 s while the video plays, get the
> last position out of a page that is closing, and resume exactly there next time, all without
> the player knowing any of it.

**Last updated:** 2026-10-17 · **Roadmap:** 10.5
**Code:**
- `core/api/learning-api.ts`: `LearningApi`, interfaces mirroring the DTOs by name, and `beacon()`
  (outside `HttpClient`, on purpose);
- `features/learn/progress-tracker.ts`: `ProgressTracker`, a service scoped to the player component;
- `shared/hls-player/`: the `playbackProgress` output (play, pause, seek, end, `timeupdate`);
- `features/learn/lecture-player/`: resume point + ticket (at once) → play, progress → tracker;
- `features/learn/my-learning/`: route `/learning` (the nav's "My Learning");
- `features/catalog/access-cta/`, `course-detail/`, `curriculum/`: "Continue learning", "Enroll for
  free", the ticks.

**Backend:** [progress LLD](../../docs/lld/progress.md) ·
[ADR-0022](../../docs/adr/0022-progress-write-back-buffer.md) ·
[API conventions §15](../../docs/api/conventions.md#15-progress-heartbeats-and-beacons)

## 1. A tracker that lives and dies with the player ⭐⭐⭐

`ProgressTracker` is `@Service({ autoProvided: false })` and listed in the player's `providers`.
One player, one tracker; when the route changes the component is destroyed, and so is everything
the tracker started:

```ts
constructor() {
  const destroyRef = inject(DestroyRef);           // ⭐ the PLAYER's DestroyRef (component injector)
  interval(HEARTBEAT_MS)
    .pipe(filter(() => this.playing), takeUntilDestroyed(destroyRef))
    .subscribe(() => this.send());
  merge(
    fromEvent(window, 'pagehide'),
    fromEvent(document, 'visibilitychange').pipe(filter(() => document.visibilityState === 'hidden')),
  )
    .pipe(takeUntilDestroyed(destroyRef))
    .subscribe(() => this.sendBeacon());
  destroyRef.onDestroy(() => this.send());          // leaving the player inside the app
}
```

- **The interval always runs; the `filter` decides.** Starting and stopping a timer on play and
  pause is the classic leak. One stream, gated by state, torn down once.
- **The `HlsPlayer` stays dumb.** It emits what the `<video>` says (`playbackProgress`); it knows
  nothing about heartbeats. The tracker knows nothing about hls.js. The player page joins them.
- **Pause, seek and end are sent at once;** `timeupdate` (the player passes on whole seconds only: each output is a change detection) only updates the
  position. **The same second twice is sent once**, so a paused player costs nothing.
- **Errors are swallowed.** A failed heartbeat retries at the next tick; nothing appears on screen.
  It's a bookmark.

Testing it without a component: `createEnvironmentInjector([ProgressTracker], parent)` gives the
tracker its own injector, and `scope.destroy()` is "the player went away"
(`progress-tracker.spec.ts`, with fake timers for the 15 s).

## 2. `sendBeacon`: the request that survives the page ⭐⭐⭐

When a tab closes, in-flight `fetch`/XHR requests are cancelled. `navigator.sendBeacon` is the
one the browser promises to deliver after the page has gone. Its limits shape the API:

| Limit | Consequence |
|---|---|
| No custom headers | No `Authorization`. The credential is **in the body**: a token each heartbeat answers (2 min, IP-bound, one user + one lecture). |
| No response | Fire and forget: `beacon()` returns only "queued or not". |
| Bypasses `HttpClient` | No interceptors (no bearer, no refresh), and `HttpTestingController` can't see it. The spec stubs `navigator.sendBeacon` and reads the `Blob`. |
| `POST` only, small body | `POST /progress/beacon` with `{token, positionSeconds}` as a JSON `Blob`. |

**Why not `fetch(…, {keepalive: true})` with the bearer?** The access token lives 15 min and may
have expired by the time the tab closes; an unloading page can't run the refresh. A beacon token
is fresh by construction.

**Which event:** `pagehide`, plus `visibilitychange` → `hidden`. `beforeunload` and `unload` don't
fire reliably on phones (and `unload` breaks the back/forward cache); "hidden" is often the last
thing a mobile page hears. Hidden → beacon is harmless if the user comes back: the next heartbeat
just sends a newer position.

## 3. Resume and ticket, asked at once ⭐⭐

```ts
const startAt = this.learning.lectureProgress(this.lectureId()).pipe(
  map((p) => p.positionSeconds),
  catchError(() => of(0)),                        // ⭐ a nicety: never block playback on it
);
this.request = forkJoin({ ticket: this.api.playback(this.lectureId()), startAt })
  .subscribe(({ ticket, startAt }) => /* HlsPlayer [src] + [startAt] */);
```

The ticket doesn't need the position, so the two requests go together: one round trip before the
first frame, not two (a simplify-pass fix).

The server answers through the write-back buffer, so a heartbeat sent a second ago already counts
(and a position in the last 10 s restarts the lecture). The re-ticketing of note 07 still resumes
at the failure's position; it never asks again.

## 4. Two freshnesses on one page set ⭐⭐

- **My Learning** reads **rollups**, up to 30 s behind. A percentage on a dashboard can lag.
- **The course page** reads **through the buffer**: the ticks and "Continue learning" are right
  the moment you come back from the player.

The course page's third `rxResource` is chained on the second (note 07): it stays idle (params
`undefined`) until the viewer is signed in **and** allowed in. Anyone else has no progress to ask
about. "Enroll for free" is an idempotent `PUT`, then `progress.reload()`.

The call to action computes one `step` signal (`ENROLL | START | CONTINUE | REVIEW`) so the
template has a single `@switch`, not nested conditions.

## 5. Pitfalls met

- **`output()` named like a DOM event** (`progress`) fails `@angular-eslint/no-output-native`: a
  parent's `(progress)` would also catch a native `progress` event bubbling from the `<video>`.
  Hence `playbackProgress`.
- **`whenStable()` hangs while a chained resource is pending:** a loading `rxResource` is a pending
  task. Tests flush with `setTimeout` + `TestBed.tick()` until the last request is answered.
- **`overrideProvider` after something was injected** throws. The course-page spec stubs the real
  `AuthStore`'s `isAuthenticated` with `vi.spyOn` instead.

## 6. Deviations from the StudyNotion Figma

The Figma couldn't be opened from this session (as in note 07), so these screens follow the
existing pages and the Figma's "Enrolled Courses" layout as remembered:

- **My Learning** is the Figma's enrolled-courses list: course name, a progress bar labelled
  "Progress N%", the tabs (All / In progress / Completed instead of All / Pending / Completed).
  **Dropped:** the thumbnails and the duration column (the API has neither for the dashboard).
  **Added:** "x of y lectures" and a Start / Continue / Review button per row, opening the lecture
  the server picked.
- **The course page** keeps its inline call to action (note 07); it now says "Continue learning"
  or "Enroll for free", plus "N% complete".
- **The curriculum** shows a tick for completed lectures in place of the play icon.

Revisit with the Figma open before Phase D5's polish.

## 7. 30-second recall

- Tracker **scoped to the component** (`providers`), one `interval` gated by a `filter`,
  `takeUntilDestroyed`, a last heartbeat in `onDestroy`.
- **`sendBeacon` on `pagehide` / hidden**, credential in the body (no headers possible), renewed by
  every heartbeat; falls back to a normal request without a valid token.
- **Resume and ticket in one `forkJoin`;** a failed resume starts at 0.
- **Dashboard lags (rollups), course page doesn't (buffer).**
