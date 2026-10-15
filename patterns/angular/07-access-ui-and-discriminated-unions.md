# 07 — Locked lectures, calls to action, and a player that re-tickets

> **One-liner:** turn the server's access decision, a **discriminated union**, into lock icons,
> the right button and a guarded player route, with the template compiler checking that each
> branch only reads the fields its shape has. Then keep a 5-minute playback token alive for an
> hour-long lecture without the viewer noticing.

**Last updated:** 2026-10-14 · **Roadmap:** 8.6
**Code:**
- `core/api/entitlement-api.ts`: `EntitlementApi`, the `AccessResponse` union;
- `shared/denial-messages.ts`: one sentence per `DenialReason`;
- `features/catalog/access-cta/`: `AccessCta`, the course page's button;
- `features/catalog/curriculum/`, `features/catalog/course-detail/`: locks, chained resource,
  `?locked=` banner;
- `features/learn/can-watch-guard.ts`, `features/learn/lecture-player/`: the player route;
- `shared/hls-player/`: reloads on a new `src` at `startAt`.

**Backend:** [entitlement LLD](../../docs/lld/entitlement-engine.md) ·
[ADR-0017](../../docs/adr/0017-short-ip-bound-playback-token.md) (the token and the re-ticket) ·
[API conventions §10–11](../../docs/api/conventions.md#10-denials-carry-a-reason)

## 1. A discriminated union, end to end ⭐⭐⭐

The API answers `{"kind": "ALLOWED", "grant": "PREVIEW_LECTURE"}` or `{"kind": "DENIED",
"reason": "NO_ENTITLEMENT"}`. The TypeScript type says exactly that:

```ts
export type AccessResponse =
  | { kind: 'ALLOWED'; grant: AccessGrant }
  | { kind: 'DENIED'; reason: DenialReason };
```

`kind` is the **discriminant**. After checking it, TypeScript knows which shape you have:

```ts
map((access) => (access.kind === 'ALLOWED' ? true : back(access.reason)))  // can-watch-guard.ts
//                                                     ^ only DENIED has `reason`: compiles
```

The alternative, `{ allowed: boolean; grant?: AccessGrant; reason?: DenialReason }`, lets you
write `if (allowed) show(reason)` and get `undefined` at runtime. The union makes that a compile
error.

The backend side is a sealed Java interface (`AccessDecision`) mapped to a Jackson
`@JsonTypeInfo(property = "kind")` DTO: **the same closed set of shapes on both sides of the wire.**

## 2. Narrowing in templates: `@let` + `@switch` ⭐⭐⭐

```html
@let a = access();
@switch (a.kind) {
  @case ('ALLOWED') { … {{ a.grant }} … }
  @case ('DENIED') {
    @switch (a.reason) {
      @case ('NO_ENTITLEMENT') { Buy / Sign in to buy }
      @case ('ENTITLEMENT_EXPIRED') { Renew access }
      @case ('ACCESS_CHECK_FAILED') { Try again }
      @default { the message, nothing to click }
    }
  }
}
```

- **Why `@let`:** `access()` is a signal *call*. The template type-checker can't assume two calls
  return the same object, so narrowing `access().kind` does nothing for the next `access().grant`.
  A `@let` is a local variable, and narrowing a variable works.
- **The `Record` trick:** `DENIAL_MESSAGES: Record<DenialReason, string>`. Add a reason to the
  union and this file stops compiling until it gets its sentence. That's the frontend's version
  of an exhaustive `switch`.
- The same union drives the player page's own state, `PlayerState` (`loading` / `playing` /
  `denied` / `failed`), switched on in `lecture-player.html`.

## 3. A resource chained on another ⭐⭐

The course page needs the course (by slug) before it can ask what the viewer may watch (by id):

```ts
protected readonly access = rxResource({
  params: () => {
    const id = this.course.value()?.id;
    return id ? { id, signedIn: this.auth.isAuthenticated() } : undefined; // ⭐ undefined = idle
  },
  stream: ({ params }) => this.entitlements.courseAccess(params.id),
});
```

- **`undefined` params keep it idle:** no request until the first resource has a value.
- **`signedIn` in the params:** signing in or out changes the params, so the locks reload. The
  value isn't sent, only *read* so the resource depends on it.
- **Testing it:** after flushing the course, the chained request appears a tick later
  (`await new Promise(r => setTimeout(r)); TestBed.tick()`), and `whenStable()` can't be used to
  wait for it, because a loading resource is a pending task.

## 4. The guard asks; the server decides ⭐⭐

```ts
{ path: 'courses/:slug/lectures/:lectureId',
  canMatch: [authGuard],        // signed in first: a playback token needs a user
  canActivate: [canWatchGuard], // then: may I? no → /courses/:slug?locked=<reason>
  loadComponent: … }
```

- `canMatch` vs `canActivate`: `authGuard` is a `CanMatch` (the chunk isn't even downloaded for a
  signed-out visitor). `canWatchGuard` needs the route's **params** (`:lectureId`), which a
  `CanActivate` gets as an `ActivatedRouteSnapshot`.
- A guard returns a **`UrlTree`** to redirect, never calls `router.navigate` itself.
- **UX only:** the server runs the same decision again on `POST /playback`. A stale tab or a
  tampered guard still gets a 403.
- `?locked=<reason>` binds to the course page's `locked` input (`withComponentInputBinding` binds
  query params too). It's **user-editable**, so the banner shows only for a reason in
  `DENIAL_MESSAGES` (`Object.hasOwn`, so `?locked=toString` shows nothing).

## 5. Re-ticketing: a 5-minute token for an hour-long lecture ⭐⭐⭐

ADR-0017 made the playback token short (5 min) and IP-bound. hls.js fetches a *new* rung
playlist whenever bandwidth changes, which can be 40 minutes in, with an expired token, which
gives a 403. The page handles it:

```ts
protected onFailed(failure: PlaybackFailure): void {
  if (Date.now() - this.ticketedAt < RETICKET_GRACE_MS) {   // failed right after a fresh token:
    this.state.set({ kind: 'failed', … });                  //   a real problem, show it
    return;
  }
  this.ticket(failure.position);                            // most likely expired: new token,
}                                                           //   resume at the same second
```

- **The re-ticket re-runs the server's decision.** A refund in the meantime comes back as a 403
  with `reason: ENTITLEMENT_REVOKED`: shown, not retried. That's how revocation reaches a viewer
  mid-lecture.
- **The grace window stops a loop:** a broken video would otherwise re-ticket forever.
- **Only a 403 is the token's fault:** hls.js reports the HTTP status (`data.response.code`), so
  a 404 or a 5xx is shown, never re-ticketed. Native Safari doesn't say, so there the grace
  window is the only filter.
- **The player reloads in place:** `HlsPlayer` runs `attach()` in an `afterRenderEffect`, so a
  new `src` destroys the old hls.js instance and starts a new one on the **same `<video>`** with
  `startPosition: startAt`. Native Safari gets `video.src = …` and a seek on `loadedmetadata`.
- `failed` now emits `{ details, position }`: the parent can't read the `<video>` inside the
  child, so the child reports where it was.

## 6. Pitfalls met here

- **Reading a field through a proxy** (backend, but the same lesson as signals): a Spring CGLIB
  proxy forwards *methods*, not fields. See [Proxy note §7b](../docs/15-proxy.md).
- **`timeout` isn't on macOS**, and **`ng test --include` with a folder glob** pulls in `.html`
  and `.scss` files: include `*.spec.ts` paths only.
- **A singleton bean's counter shared between tests** (backend): assert deltas, not totals.

## 7. Deviations from the StudyNotion Figma

The Figma couldn't be opened from this session, so this slice follows the existing course page
layout (Material 3 tokens, `course-detail.scss`) and these choices:

- **The purchase card is an inline call to action** under the price, not the Figma's floating
  side card with "Buy Now" and "Add to Cart". Cart and checkout arrive in Phase 9; restyle the
  CTA into the card then.
- **"Buy now" is disabled** with "Checkout is coming soon" until Phase 9. *(Phase 9.9: it puts
  the course in the cart and goes there; see [note 08](08-cart-checkout-and-third-party-scripts.md).)*
- **Lock icons** replace the Figma's plain lecture rows, and an open video's title is a link to
  the player.
- **The player page is minimal** (back link + video): the Figma's lecture sidebar and progress
  come with Phase 10 (My Learning, resume).

## Interview Q&A

- **Q:** Why a discriminated union instead of optional fields?
  **A:** Impossible states become unrepresentable: a denial without a reason, or an allow with
  one, doesn't compile. And narrowing on the discriminant tells the compiler which fields exist.
- **Q:** Why does narrowing need `@let` in a template?
  **A:** A signal read is a function call, and two calls may return different objects. A local
  variable is narrowed once and stays narrowed.
- **Q:** The guard says yes. Is the lecture safe?
  **A:** No, a guard is UX. The server decides again when the player asks for a token, and the
  token itself is what the playlists accept.
- **Q:** How does a 5-minute token survive an hour of video?
  **A:** On a playback 403 the page asks for a new token and resumes at the same second. The new
  token re-runs the access decision, so a refund takes effect at the next fetch. A failure right
  after a fresh token is shown instead of retried.

## 30-second recall

- **Union:** `kind` discriminant, `Record<Reason, string>` for copy, `@let` + `@switch` to narrow.
- **Chained `rxResource`:** `undefined` params = idle; read `signedIn` to reload on login.
- **Guard:** `canMatch` (auth) + `canActivate` (entitlement, needs params) → `UrlTree` with `?locked=`.
- **Re-ticket:** on failure, new token (server re-decides) + `startAt`; a grace window stops loops;
  `HlsPlayer` reloads the same `<video>`.
