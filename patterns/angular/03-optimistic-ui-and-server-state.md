# Angular 03: Optimistic UI, server state in signals, and links that must not act on GET

> **One-liner:** show the user's change **immediately**, send it in the background, and **roll
> back** if the server says no. Keep server data in signals updated **immutably**, guard against
> racing requests, and never let merely *opening* a page change state.

**Last updated:** 2026-10-02 · **Roadmap:** 4.7
**Code:**
- [`core/api/notification-api.ts`](../../frontend/src/app/core/api/notification-api.ts): typed client + DTO mirrors + category copy.
- [`features/account/notifications/`](../../frontend/src/app/features/account/notifications/) (route `/account/notifications`): optimistic toggles.
- [`features/unsubscribe/`](../../frontend/src/app/features/unsubscribe/) (route `/unsubscribe?token=…`): public, confirm-then-POST.

**Backend:** [`docs/api/conventions.md`](../../docs/api/conventions.md) §14 · **Design:** [`docs/lld/notification.md`](../../docs/lld/notification.md)

**Priority marks:** ⭐⭐⭐ must know · ⭐⭐ use daily · ⭐ good to know.

---

## 1. Pessimistic vs optimistic updates ⭐⭐⭐

| | Pessimistic | Optimistic |
|---|---|---|
| when the UI changes | after the server answers | **immediately** |
| user waits | spinner on every click | never (for the common case) |
| on failure | nothing to undo | **roll back** + tell the user |
| right for | payments, deletes, anything irreversible or likely to fail | toggles, likes, reordering: cheap, reversible, almost always succeed |

A notification toggle is the textbook optimistic case. It's a single boolean, the PUT is
idempotent, and failure is rare (network blip, 5xx). Waiting 200 ms per click would make the page
feel broken.

## 2. The three steps in code ⭐⭐⭐

```ts
protected toggle(category: NotificationCategory, enabled: boolean): void {
  this.setEnabled(category, enabled);                 // ⭐ 1. apply locally, now
  this.markSaving(category, true);                    //    and lock that one toggle
  this.api.setPreference(category, enabled)
    .pipe(finalize(() => this.markSaving(category, false)))   // unlock on success AND error
    .subscribe({
      error: () => {
        this.setEnabled(category, !enabled);          // ⭐ 2. roll back to what the server has
        this.snackBar.open(`Couldn't save "${label}". Please try again.`, 'Dismiss', { duration: 5000 });
      },                                              // ⭐ 3. tell the user (never fail silently)
    });
}
```

**Why roll back to `!enabled`** and not "reload from the server"? The toggle has only two values,
and the previous one is known. For richer state (a reordered list), keep a snapshot before the
change (`const before = this.items()`) and restore it. Re-fetching is the fallback when you
can't reconstruct the old state.

## 3. Races: two clicks, two requests ⭐⭐⭐

Click off, then on, quickly. Two PUTs fly. If the *first* one arrives last, the server ends up
"off" while the UI shows "on". Options:

| Strategy | How | Trade-off |
|---|---|---|
| **lock while in flight** (used here) | `[disabled]="saving().has(p.category)"` | simplest; one click per ~100 ms is fine for toggles |
| latest wins | `switchMap` per key (a `Subject` per category) | cancels the older request **client-side**, but the server may still have processed it |
| sequence numbers | send a version and let the server reject stale writes (409) | needed when several clients edit the same thing (Phase 6 `@Version`) |

The lock is per category: toggling *News* doesn't freeze *Reviews*. That's why `saving` is a
`Set<NotificationCategory>`, not one boolean.

## 4. Server state in signals: immutable updates ⭐⭐⭐

```ts
protected readonly preferences = signal<NotificationPreferenceResponse[]>([]);

private setEnabled(category: NotificationCategory, enabled: boolean): void {
  this.preferences.update((list) =>
    list.map((p) => (p.category === category ? { ...p, enabled } : p)),   // ⭐ new array, new object
  );
}
```

- ⭐⭐⭐ **A signal notifies only when its value changes by `Object.is`.** `list[0].enabled =
  false; this.preferences.set(list)` sets the *same* array: no change, no re-render.
- **`update(fn)`** for "derive the next value from the current one"; **`set(v)`** for
  replacing it wholesale (the initial load).
- **Same with `Set`:** `saving.update(s => { const next = new Set(s); next.add(c); return next; })`.
  Mutating the existing `Set` in place would not notify.
- `@for (p of preferences(); track p.category)`: **track by a stable key**, so Angular updates
  the one changed row instead of re-creating all five toggles (which would lose focus and
  animation).

## 5. A small "load state" machine instead of booleans ⭐⭐

```ts
type LoadState = 'loading' | 'ready' | 'error';
protected readonly state = signal<LoadState>('loading');
```

```html
@switch (state()) {
  @case ('loading') { <mat-spinner /> }
  @case ('error')   { couldn't load · <button (click)="load()">Retry</button> }
  @case ('ready')   { …toggles… }
}
```

`isLoading` + `hasError` booleans allow impossible combinations (both true). A union type
allows exactly one state at a time, the frontend twin of the backend's sealed `Claim`.
The unsubscribe page does the same with five states:
`'confirm' | 'working' | 'done' | 'invalid' | 'missing'`.

## 6. Pages that must not act on load ⭐⭐⭐

The unsubscribe link sits in an email. **Mail scanners, link previewers and corporate security
gateways open every URL in an email**, and some run JavaScript. So:

- The URL is a **page** (`/unsubscribe?token=…`), not the API.
- `ngOnInit` **only reads** the token. The POST happens in `confirm()`, on a **human click**.
- The spec proves it: `does nothing on load: a link scanner opening the page unsubscribes nobody`
  (`http.expectNone(...)`).

Compare `/verify-email`, which **does** POST on load. Verifying is harmless if a scanner does it
(it proves the mailbox exists, which is the point), and it's single-use. **Decide per link: what
happens if a robot opens it?**

Gmail's own "Unsubscribe" button doesn't use this page. It POSTs the `List-Unsubscribe` header
URL directly (RFC 8058), which the API accepts as a form post.

## 7. Where API clients live ⭐⭐

- `core/api/notification-api.ts` is in **core** because two features use it (account settings and
  the public unsubscribe page).
- `features/admin/admin-api.ts` is in its **feature**, because only the admin page uses it. It then
  ships in the admin's lazy chunk.
- **DTO interfaces mirror the backend records by name** (`NotificationPreferenceResponse`,
  `UnsubscribeResponse`), so a grep finds both sides.
- **Presentation copy** (`CATEGORY_COPY`: labels, hints) is a frontend concern. The API returns
  stable enum codes; the UI decides how to say them. `Record<NotificationCategory, …>` makes the
  compiler demand copy for every category: add a category to the type and the build fails until
  it has a label.

## 8. Testing Material components without harnesses ⭐⭐

`<mat-slide-toggle>` renders a real `<button role="switch" aria-checked="…">`. Specs drive it the
way a user (or a screen reader) sees it:

```ts
const button = query<HTMLButtonElement>(fixture, '[data-testid="toggle-PRODUCT_NEWS"] button');
button.click();
await fixture.whenStable();
expect(button.getAttribute('aria-checked')).toBe('false');   // optimistic: flipped before the response
expect(button.disabled).toBe(true);                           // locked while in flight
```

Asserting on **ARIA attributes** tests accessibility for free. Component harnesses
(`MatSlideToggleHarness`) are the heavier alternative; worth it for complex widgets (table,
select, datepicker) in Phases 5–6.

The four specs:

| Spec | Proves |
|---|---|
| `shows every category, with mandatory ones locked on` | `[disabled]="p.mandatory"` |
| `flips the toggle immediately and PUTs in the background` | ⭐ optimistic + per-key lock |
| `rolls the toggle back when the server refuses` | ⭐ rollback + snackbar text |
| `offers a retry when loading fails` | the load-state machine |

## 9. Common mistakes ⭐⭐⭐

- **Mutating signal contents in place:** no re-render (§4).
- **Optimistic update without rollback:** the UI lies until the next reload.
- **A single global `saving` boolean:** one slow request freezes every toggle.
- **Doing the action in `ngOnInit` for links in emails:** robots trigger it (§6).
- **`finalize` forgotten:** an error leaves the toggle locked forever.
- **Showing the server's `detail` text as UI copy:** branch on `code`, write your own words
  (note 02 §10).

## 10. Interview Q&A

- **Q: When would you *not* use optimistic UI?**
  **A:** When the action is irreversible, expensive or likely to fail: payments, deletes,
  anything with server-side validation the client can't predict. Then show progress and wait.
- **Q: How do you handle out-of-order responses?**
  **A:** Lock the control while in flight, or `switchMap` to cancel the older request
  client-side, or version the writes so the server rejects stale ones. Pick by how many clients
  can edit the same thing.
- **Q: Why didn't my signal update the view?**
  **A:** The new value is `===` the old one: the array or object was mutated in place. Return a
  new reference from `update`.
- **Q: Why does the unsubscribe page need a button?**
  **A:** Link scanners open URLs from emails; a GET (or a page that POSTs on load) would
  unsubscribe people who never clicked.

## 11. 30-second recall

- **Optimistic:** apply locally → request → on error roll back + snackbar; `finalize` unlocks.
- **Races:** a per-key in-flight lock (`Set` in a signal); alternatives are `switchMap` or
  server-side versions.
- **Signals:** immutable updates (`map` + spread, `new Set`); `track` by a stable key.
- **State:** a union type (`'loading' | 'ready' | 'error'`), not a pile of booleans.
- **Email links:** a page that waits for a human click; one-click unsubscribe goes header → API.
