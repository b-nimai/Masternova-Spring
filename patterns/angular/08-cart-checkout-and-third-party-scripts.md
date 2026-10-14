# 08 — A cart the server prices, a third-party checkout, and a page that waits for a webhook

> **One-liner:** keep the cart in the browser but never trust its numbers, open Razorpay's
> checkout (a script we don't own) behind a Promise, and show the truth about a payment even when
> the browser's own confirmation never arrives.

**Last updated:** 2026-10-14 · **Roadmap:** 9.9
**Code:**
- `core/api/commerce-api.ts`: `CommerceApi`, interfaces mirroring the DTOs by name;
- `core/commerce/cart-store.ts`: `CartStore`, a signal store persisted to `localStorage`;
- `core/commerce/razorpay-checkout.ts`: `RazorpayCheckout`, the lazily loaded script;
- `features/commerce/checkout-flow.ts`: `CheckoutFlow`, order → widget → confirm;
- `features/commerce/fake-checkout-dialog/`: the dev stand-in for Razorpay's modal;
- `features/commerce/cart/`, `order-result/`, `order-history/`: routes `/cart`, `/orders/:id`,
  `/account/orders`;
- `features/catalog/access-cta/`: "Buy now", "Add to cart", "Renew access"; the toolbar's badge.

**Backend:** [commerce LLD](../../docs/lld/order-state-machine.md) ·
[ADR-0018](../../docs/adr/0018-claim-webhook-events-before-processing.md) ·
[ADR-0019](../../docs/adr/0019-grant-access-in-the-order-transaction.md) ·
[API conventions §4, §5, §12](../../docs/api/conventions.md)

## 1. The cart: a store in the browser, prices from the server ⭐⭐⭐

The cart is a list of courses in a signal store (`CartStore`): a private `signal`, read-only
views (`items`, `count`, `courseIds`, `isFull`), and named actions (`add`, `remove`,
`removeAll`). Every change is written to `localStorage`, so the cart survives a reload and the trip
to `/login`. Reading it back is defensive: storage can be missing, blocked or edited by hand, so
`load()` catches, checks it's an array, and keeps only well-formed items.

The prices in the store are **for display only**. The cart page's numbers come from
`POST /cart/quote`, and checkout sends **course ids and a code, never an amount**. A price changed
by the instructor, a course bought on another device, a coupon that just expired: the server is the
only one who knows, so the server prices.

```ts
protected readonly quote = rxResource({
  params: () => {
    const ids = this.cart.courseIds();
    return ids.length ? { ids, coupon: this.appliedCoupon() } : undefined; // ⭐ idle when empty
  },
  stream: ({ params }) => this.api.quote(params.ids, params.coupon),
});
```

Removing a course or applying a coupon changes `params`, so the resource re-prices and cancels a
request still in flight.

**Applying a coupon is a quote with it.** The field calls `quote(ids, code)` once. If the server
says `COUPON_EXPIRED`, the message goes under the field and the current prices stay. If it accepts,
`appliedCoupon` is set to the **normalized** code from the answer (`save10` → `SAVE10`), and the
resource re-prices with it.

## 2. A third-party script behind a Promise ⭐⭐⭐

Razorpay Checkout is a `<script>` from `checkout.razorpay.com` that defines a global
`Razorpay` constructor. Three rules for loading it:

1. **Lazily, once.** The tag is added the first time someone pays, never at page load, so no
   third-party code runs on pages that don't need it. Concurrent callers share one `Promise`
   (`this.loading ??= …`).
2. **A failed load can be retried.** `onerror` resets the promise, so the next click tries again
   (flaky network, an ad blocker that changed its mind).
3. **Through `DOCUMENT`, not `window`/`document` globals**, so the service is testable: the spec
   replaces the global with a fake constructor and fires the tag's `onload` itself.

The widget is callback-based (`handler`, `modal.ondismiss`). `pay()` wraps it in a `Promise` that
resolves with a discriminated union:

```ts
type WidgetResult = { kind: 'paid'; proof: PaymentProof } | { kind: 'dismissed' };
```

**Zoneless, no `NgZone.run`.** Razorpay calls `handler` from code Angular never patched. With
zone.js that callback wouldn't trigger change detection, and the classic fix was
`ngZone.run(() => …)`. The app is zoneless: the callback resolves a Promise, the caller sets
signals, and signals schedule their own rendering.

## 3. One flow, a widget per provider ⭐⭐

`CheckoutFlow.checkout(coupon)` is the browser's half of the backend's Facade: place the order
(with a fresh `Idempotency-Key` per attempt), open the provider's widget, confirm the payment
proof, and remove only the **bought** courses from the cart.

The widget is picked by the provider **the server names** in the checkout response
(`payment.provider`), mirroring the backend's `PaymentGateway` Strategy:

| `payment.provider` | Widget |
|---|---|
| `RAZORPAY` | `RazorpayCheckout.pay(…)`, prefilled with the user's name and email |
| `FAKE` | `FakeCheckoutDialog` (dev, e2e, CI: no Razorpay keys). "Pay" asks the dev-only endpoint for what Razorpay's `handler` would get, a payment id and its signature, and the flow confirms it exactly as for Razorpay |
| `payment === null` | a free order (a 100% coupon), already `PAID`: no widget |

The cart page never knows which ran. A `dismissed` widget leaves the order `PENDING` and the cart
untouched ("Payment cancelled. Nothing was charged.").

## 4. The result page polls while the webhook is on its way ⭐⭐⭐

The browser's `confirm` is one of **two** messengers (LLD §1). If the tab closes after paying,
`confirm` never arrives, but Razorpay's webhook pays the order seconds later. So `/orders/:id`
doesn't trust that it saw the payment: while the order is `PENDING`, it asks again.

```ts
stream: ({ params: id }) =>
  timer(0, POLL_MS).pipe(
    take(POLL_TIMES + 1),                            // 30 s at most
    switchMap(() => this.api.order(id)),
    takeWhile((o) => o.status === 'PENDING', true),  // ⭐ inclusive: the final state is emitted
  ),
```

An `rxResource` stream can emit many values, and the page updates with each. The `true` in
`takeWhile` matters: without it the stream would complete *before* emitting `PAID`, and the page
would stay on "Confirming your payment…". After 30 s it stops and keeps saying it's waiting; the
order history shows the final state whenever it lands.

## 5. Pitfalls met here

- **`resource.value()` throws in the error state.** `@if (quote.value(); as q) … @else if
  (quote.error())` crashed the page when a quote failed with a 422. Guard with `hasValue()`:
  `@if (quote.hasValue() ? quote.value() : null; as q)`. Found by the spec for an already-owned
  course.
- **`(ngSubmit)` needs a form directive.** A `<form (ngSubmit)>` around a lone
  `[formControl]` never emits (there's no `NgForm` or `FormGroupDirective` on it), so "Apply" did
  nothing. Found by the coupon spec, fixed with a `FormGroup` and `formControlName`.
- **`*matCellDef="let o"` is `any`.** `labels[o.status]` didn't compile under strict templates.
  Typed helper methods (`label(o: OrderResponse)`) instead.
- **Testing zoneless resources:** `TestBed.tick()` to let the resource send its request, then
  flush, then a microtask + `TestBed.tick()` to render the value. Fake timers for the poll fake only
  timers, so promises still resolve.

## 6. Deviations from the StudyNotion Figma

The Figma couldn't be opened from this session, so these screens follow the existing pages (Material
3 tokens, the course page's spacing) and these choices:

- **The cart is two columns** (items; a summary card with the coupon field and the button), which
  is the Figma's cart layout, but without the course thumbnails and ratings: `CartItem` holds only
  what checkout needs.
- **The purchase card on the course page stays an inline call to action** ("Buy now" + "Add to
  cart" under the price), not the Figma's floating side card.
- **Added, not in the Figma:** the simulated-payment dialog (dev only), the "Confirming your
  payment…" state, the order history table and its status chips.

## Interview Q&A

- **Q: The cart has prices. Why does checkout not send them?**
  **A:** They're a snapshot from when the course was added. The server re-prices from the catalog,
  the coupon rules and what the buyer already owns. Sending an amount would let any client pick
  its own price.
- **Q: How do you load a third-party payment script in Angular?**
  **A:** Lazily, on the first click, through `DOCUMENT`; one shared Promise for concurrent callers;
  reset it on `onerror` so a retry works; wrap the callback API in a Promise that resolves to a
  union (`paid` / `dismissed`).
- **Q: Do you need `NgZone.run` in Razorpay's handler?**
  **A:** Not in a zoneless app: signals schedule change detection themselves. With zone.js, yes,
  because the callback runs outside the zone.
- **Q: The user paid and closed the tab. What do they see later?**
  **A:** The webhook paid the order and granted the course, in one transaction. The order history
  shows `PAID`, and the result page polls while `PENDING`, so it updates without a reload.

## 30-second recall

- **Cart:** a signal store in `localStorage`, defensive load; display prices only; the server
  quotes (`rxResource` over ids + coupon) and re-prices at checkout.
- **Third-party script:** lazy, once, shared Promise, retry on error, `DOCUMENT`; callbacks →
  a Promise of a union; zoneless needs no `NgZone`.
- **Flow:** order (Idempotency-Key) → the widget the server names (Razorpay / fake / none) →
  confirm → remove only what was bought.
- **Result page:** poll while `PENDING` with `takeWhile(…, true)`, stop at a final state or 30 s.
