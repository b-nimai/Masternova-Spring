import { computed, Service, signal } from '@angular/core';

/** One course in the cart: what the cart page shows before the server prices it. */
export interface CartItem {
  courseId: string;
  slug: string;
  title: string;
  priceMinor: number;
  currency: string;
}

const STORAGE_KEY = 'masternova.cart.v1';
/** The server's `masternova.checkout.cart-max-items`; the server checks it again (CART_TOO_LARGE). */
export const CART_MAX_ITEMS = 20;

/**
 * The cart: a signal STORE kept in the browser (commerce LLD §6 — the server prices it on demand,
 * nothing about it needs to survive a device change yet).
 *
 * ⭐ The writable signal is private; components get read-only views and named actions. Every
 *    change is written to localStorage, so the cart survives a reload and the trip to /login.
 * ⭐ The prices here are for DISPLAY only: checkout sends course ids, and the server re-prices.
 */
@Service()
export class CartStore {
  private readonly state = signal<readonly CartItem[]>(load());

  readonly items = this.state.asReadonly();
  readonly count = computed(() => this.state().length);
  readonly courseIds = computed(() => this.state().map((i) => i.courseId));
  readonly isFull = computed(() => this.state().length >= CART_MAX_ITEMS);

  has(courseId: string): boolean {
    return this.state().some((i) => i.courseId === courseId);
  }

  add(item: CartItem): void {
    if (this.has(item.courseId) || this.isFull()) {
      return;
    }
    this.set([...this.state(), item]);
  }

  remove(courseId: string): void {
    this.set(this.state().filter((i) => i.courseId !== courseId));
  }

  /** After a purchase: only the courses that were bought leave the cart. */
  removeAll(courseIds: readonly string[]): void {
    const bought = new Set(courseIds);
    this.set(this.state().filter((i) => !bought.has(i.courseId)));
  }

  private set(items: readonly CartItem[]): void {
    this.state.set(items);
    save(items);
  }
}

// localStorage can be missing or throw (private mode, blocked storage): the cart then lives in
// memory for this tab only, which is still a working cart.
function load(): readonly CartItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? (parsed as CartItem[]).filter(isCartItem) : [];
  } catch {
    return [];
  }
}

function save(items: readonly CartItem[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    // ignore: see load()
  }
}

function isCartItem(value: unknown): value is CartItem {
  const v = value as Partial<CartItem> | null;
  return (
    typeof v?.courseId === 'string' &&
    typeof v.slug === 'string' &&
    typeof v.title === 'string' &&
    typeof v.priceMinor === 'number' &&
    typeof v.currency === 'string'
  );
}
