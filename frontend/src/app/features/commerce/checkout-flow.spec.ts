import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { CheckoutResponse, CommerceApi } from '../../core/api/commerce-api';
import { AuthStore } from '../../core/auth/auth-store';
import { CartStore } from '../../core/commerce/cart-store';
import { RazorpayCheckout, WidgetResult } from '../../core/commerce/razorpay-checkout';
import { JAVA, SPRING, orderOf } from '../../../testing/commerce-fixtures';
import { CheckoutFlow } from './checkout-flow';

describe('CheckoutFlow', () => {
  const api = {
    checkout: vi.fn(),
    confirm: vi.fn(),
  };
  const razorpay = { pay: vi.fn<(...args: unknown[]) => Promise<WidgetResult>>() };
  const dialog = { open: vi.fn() };
  let cart: CartStore;
  let flow: CheckoutFlow;

  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: CommerceApi, useValue: api },
        { provide: RazorpayCheckout, useValue: razorpay },
        { provide: MatDialog, useValue: dialog },
        {
          provide: AuthStore,
          useValue: { user: () => ({ displayName: 'Lena', email: 'lena@example.com' }) },
        },
      ],
    });
    cart = TestBed.inject(CartStore);
    cart.add(JAVA);
    cart.add(SPRING);
    flow = TestBed.inject(CheckoutFlow);
  });

  function placed(provider: 'RAZORPAY' | 'FAKE' | null): CheckoutResponse {
    return {
      order: orderOf('PENDING', [JAVA]),
      payment: provider && {
        provider,
        publicKey: 'k',
        providerOrderId: 'order_1',
        amountMinor: 49900,
        currency: 'INR',
      },
    };
  }

  it('pays with Razorpay, confirms, and empties the cart of what was bought', async () => {
    api.checkout.mockReturnValue(of(placed('RAZORPAY')));
    razorpay.pay.mockResolvedValue({ kind: 'paid', proof: { paymentId: 'pay_1', signature: 's' } });
    api.confirm.mockReturnValue(of(orderOf('PAID', [JAVA])));

    const outcome = await flow.checkout('SAVE20');

    expect(api.checkout).toHaveBeenCalledWith(['c-java', 'c-spring'], 'SAVE20', expect.any(String));
    expect(razorpay.pay.mock.calls[0][2]).toEqual({ name: 'Lena', email: 'lena@example.com' });
    expect(api.confirm).toHaveBeenCalledWith('o-1', { paymentId: 'pay_1', signature: 's' });
    expect(outcome.kind).toBe('done');
    expect(cart.courseIds()).toEqual(['c-spring']); // ⭐ only the bought course left the cart
  });

  it('never sends a buyer who paid back to the Checkout button, even if confirm fails', async () => {
    // review fix: a failed confirm surfaced on the cart with the items still there → a 2nd charge
    api.checkout.mockReturnValue(of(placed('RAZORPAY')));
    razorpay.pay.mockResolvedValue({ kind: 'paid', proof: { paymentId: 'pay_1', signature: 's' } });
    api.confirm.mockReturnValue(throwError(() => new Error('network down')));

    const outcome = await flow.checkout(null);

    expect(outcome).toEqual({ kind: 'done', order: placed('RAZORPAY').order }); // → the result page polls
    expect(cart.courseIds()).toEqual(['c-spring']);
  });

  it('uses a new Idempotency-Key per attempt', async () => {
    api.checkout.mockReturnValue(of(placed(null)));
    await flow.checkout(null);
    cart.add(JAVA);
    await flow.checkout(null);
    expect(api.checkout.mock.calls[0][2]).not.toBe(api.checkout.mock.calls[1][2]);
  });

  it('opens the simulator for the FAKE provider and nothing for a free order', async () => {
    api.checkout.mockReturnValue(of(placed('FAKE')));
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) }); // closed by Escape
    expect((await flow.checkout(null)).kind).toBe('dismissed');
    expect(api.confirm).not.toHaveBeenCalled();
    expect(cart.count()).toBe(2); // nothing bought, nothing removed

    api.checkout.mockReturnValue(of({ order: orderOf('PAID', [JAVA]), payment: null }));
    expect((await flow.checkout('GIFT')).kind).toBe('done');
    expect(razorpay.pay).not.toHaveBeenCalled();
  });
});
