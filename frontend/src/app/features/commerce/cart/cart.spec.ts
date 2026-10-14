import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CartStore } from '../../../core/commerce/cart-store';
import { JAVA, SPRING, orderOf, quoteOf } from '../../../../testing/commerce-fixtures';
import { CheckoutFlow } from '../checkout-flow';
import { Cart } from './cart';

describe('Cart', () => {
  let fixture: ComponentFixture<Cart>;
  let http: HttpTestingController;
  let cart: CartStore;
  const flow = { checkout: vi.fn() };

  beforeEach(async () => {
    localStorage.clear();
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [Cart],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CheckoutFlow, useValue: flow },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    cart = TestBed.inject(CartStore);
  });

  afterEach(() => http.verify());

  /** ⭐ Zoneless: `TestBed.tick()` runs effects (the resource sends its request) and renders. */
  function render(): HTMLElement {
    fixture = TestBed.createComponent(Cart);
    TestBed.tick();
    return fixture.nativeElement as HTMLElement;
  }

  /** Let a resource's value land (a microtask), then render. */
  async function settle(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
    TestBed.tick();
  }

  async function quote(body: object, status = 200) {
    const req = http.expectOne('/api/v1/cart/quote');
    if (status === 200) {
      req.flush(body);
    } else {
      req.flush(body, { status, statusText: 'x' });
    }
    await settle();
    return req.request.body as { courseIds: string[]; couponCode: string | null };
  }

  it('says so when the cart is empty, and asks the server nothing', async () => {
    expect(render().querySelector('[data-testid="cart-empty"]')!.textContent).toContain(
      'Your cart is empty',
    );
  });

  it('shows the SERVER’s prices and re-prices when a course is removed', async () => {
    cart.add(JAVA);
    cart.add(SPRING);
    const el = render();
    expect((await quote(quoteOf([JAVA, SPRING]))).courseIds).toEqual(['c-java', 'c-spring']);
    expect(el.querySelector('[data-testid="total"]')!.textContent).toContain('1,298');

    el.querySelector<HTMLButtonElement>('[aria-label="Remove Java from zero"]')!.click();
    TestBed.tick();
    expect((await quote(quoteOf([SPRING]))).courseIds).toEqual(['c-spring']);
    expect(el.querySelectorAll('[data-testid="cart-item"]').length).toBe(1);
  });

  it('applies a coupon only after the server accepted it, and explains a refusal', async () => {
    cart.add(JAVA);
    const el = render();
    await quote(quoteOf([JAVA]));

    const input = el.querySelector<HTMLInputElement>('[data-testid="coupon-input"]')!;
    input.value = 'old';
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    TestBed.tick();
    await quote({ status: 422, code: 'COUPON_EXPIRED', detail: 'x' }, 422);
    expect(el.querySelector('[data-testid="coupon-error"]')!.textContent).toContain('expired');
    expect(el.querySelector('[data-testid="total"]')!.textContent).toContain('499'); // untouched

    input.value = 'save10';
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    TestBed.tick();
    expect((await quote(quoteOf([JAVA], 4990, 'SAVE10'))).couponCode).toBe('save10');
    // ⭐ accepted: the resource re-prices WITH the normalized code
    expect((await quote(quoteOf([JAVA], 4990, 'SAVE10'))).couponCode).toBe('SAVE10');
    expect(el.querySelector('[data-testid="discount"]')!.textContent).toContain('49.90');
    expect(el.querySelector('[data-testid="applied-coupon"]')!.textContent).toContain('SAVE10');
  });

  it('blocks checkout when the cart can’t be sold, with the reason', async () => {
    cart.add(JAVA);
    const el = render();
    await quote({ status: 422, code: 'COURSE_ALREADY_OWNED', courseId: 'c-java' }, 422);
    expect(el.querySelector('[data-testid="quote-error"]')!.textContent).toContain('already have');
    expect(el.querySelector<HTMLButtonElement>('[data-testid="checkout"]')!.disabled).toBe(true);
  });

  it('checks out and lands on the order’s result page', async () => {
    cart.add(JAVA);
    const el = render();
    await quote(quoteOf([JAVA]));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    flow.checkout.mockResolvedValue({ kind: 'done', order: orderOf('PAID') });

    el.querySelector<HTMLButtonElement>('[data-testid="checkout"]')!.click();
    await settle();

    expect(flow.checkout).toHaveBeenCalledWith(null);
    expect(navigate).toHaveBeenCalledWith(['/orders', 'o-1']);
  });

  it('shows why checkout failed (the provider is down)', async () => {
    cart.add(JAVA);
    const el = render();
    await quote(quoteOf([JAVA]));
    flow.checkout.mockRejectedValue(
      new HttpErrorResponse({
        status: 503,
        error: { status: 503, code: 'PAYMENT_PROVIDER_UNAVAILABLE' },
      }),
    );

    el.querySelector<HTMLButtonElement>('[data-testid="checkout"]')!.click();
    await settle();

    expect(el.querySelector('[data-testid="checkout-error"]')!.textContent).toContain(
      'temporarily unavailable',
    );
  });
});
