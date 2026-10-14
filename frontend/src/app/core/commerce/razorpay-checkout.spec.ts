import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PaymentDetails } from '../api/commerce-api';
import { RAZORPAY_SCRIPT, RazorpayCheckout } from './razorpay-checkout';

const PAYMENT: PaymentDetails = {
  provider: 'RAZORPAY',
  publicKey: 'rzp_test_1',
  providerOrderId: 'order_1',
  amountMinor: 49900,
  currency: 'INR',
};

/** A stand-in for Razorpay's global: records the options and lets the test act as the buyer. */
class FakeRazorpay {
  static last: FakeRazorpay;
  constructor(
    readonly options: Record<string, unknown> & {
      handler: (r: Record<string, string>) => void;
      modal: { ondismiss: () => void };
    },
  ) {
    FakeRazorpay.last = this;
  }
  on = vi.fn();
  open = vi.fn();
}

describe('RazorpayCheckout', () => {
  let document: Document;
  let window: Window & { Razorpay?: unknown };

  beforeEach(() => {
    document = TestBed.inject(DOCUMENT);
    window = document.defaultView as Window & { Razorpay?: unknown };
    delete window.Razorpay;
    document.querySelectorAll(`script[src="${RAZORPAY_SCRIPT}"]`).forEach((s) => s.remove());
  });

  it('loads the script once, lazily, and resolves with the payment proof', async () => {
    const checkout = TestBed.inject(RazorpayCheckout);
    const paying = checkout.pay(PAYMENT, 'Java', { email: 'lena@example.com' });

    const scripts = document.querySelectorAll<HTMLScriptElement>(
      `script[src="${RAZORPAY_SCRIPT}"]`,
    );
    expect(scripts.length).toBe(1);
    window.Razorpay = FakeRazorpay;
    scripts[0].onload!(new Event('load'));
    await Promise.resolve();
    await Promise.resolve();

    const options = FakeRazorpay.last.options;
    expect(options['order_id']).toBe('order_1');
    expect(options['amount']).toBe(49900); // paise, unchanged
    expect(options['key']).toBe('rzp_test_1');
    expect(FakeRazorpay.last.open).toHaveBeenCalled();

    // ⭐ Razorpay calls back from outside Angular: the Promise still resolves
    options.handler({
      razorpay_payment_id: 'pay_1',
      razorpay_order_id: 'order_1',
      razorpay_signature: 'sig',
    });
    expect(await paying).toEqual({
      kind: 'paid',
      proof: { paymentId: 'pay_1', signature: 'sig' },
    });

    // a second payment reuses the loaded global: no second script tag
    const again = checkout.pay(PAYMENT, 'Java', {});
    await Promise.resolve();
    FakeRazorpay.last.options.modal.ondismiss();
    expect(await again).toEqual({ kind: 'dismissed' });
    expect(document.querySelectorAll(`script[src="${RAZORPAY_SCRIPT}"]`).length).toBe(1);
  });

  it('rejects when the script can’t load, and a later click tries again', async () => {
    const checkout = TestBed.inject(RazorpayCheckout);
    const paying = checkout.pay(PAYMENT, 'Java', {});
    document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SCRIPT}"]`)!.onerror!(
      new Event('error'),
    );
    await expect(paying).rejects.toThrow('could not be loaded');

    void checkout.pay(PAYMENT, 'Java', {}).catch(() => undefined);
    expect(document.querySelectorAll(`script[src="${RAZORPAY_SCRIPT}"]`).length).toBe(2);
  });
});
