import { DOCUMENT, inject, Service } from '@angular/core';
import { PaymentDetails, PaymentProof } from '../api/commerce-api';

/** The slice of Razorpay Checkout's options we use (https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/). */
interface RazorpayOptions {
  key: string;
  amount: number;
  currency: string;
  order_id: string;
  name: string;
  description: string;
  prefill?: { name?: string; email?: string };
  handler: (response: {
    razorpay_payment_id: string;
    razorpay_order_id: string;
    razorpay_signature: string;
  }) => void;
  modal: { ondismiss: () => void };
}

interface RazorpayInstance {
  open(): void;
  on(event: 'payment.failed', handler: (response: unknown) => void): void;
}

type RazorpayConstructor = new (options: RazorpayOptions) => RazorpayInstance;

export const RAZORPAY_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

/** How a checkout widget ended: paid (with the proof to confirm), or closed by the buyer. */
export type WidgetResult = { kind: 'paid'; proof: PaymentProof } | { kind: 'dismissed' };

/**
 * Razorpay Checkout, a THIRD-PARTY SCRIPT, behind a Promise.
 *
 * ⭐ Loaded lazily, once: the `<script>` tag is added the first time someone pays, never on page
 *    load (no third-party code on pages that don't need it). Concurrent callers share one load.
 * ⭐ Zoneless: Razorpay calls `handler` outside anything Angular knows about. That's fine — the
 *    caller resolves a Promise and sets signals, and signals schedule their own change detection.
 *    No `NgZone.run`.
 */
@Service()
export class RazorpayCheckout {
  private readonly document = inject(DOCUMENT);
  private loading: Promise<RazorpayConstructor> | null = null;

  /** Opens the modal; resolves when the buyer has paid or closed it. */
  async pay(
    payment: PaymentDetails,
    description: string,
    prefill: { name?: string; email?: string },
  ): Promise<WidgetResult> {
    const Razorpay = await this.load();
    return new Promise<WidgetResult>((resolve) => {
      const checkout = new Razorpay({
        key: payment.publicKey,
        amount: payment.amountMinor, // ⭐ paise, like everything else (API conventions §5)
        currency: payment.currency,
        order_id: payment.providerOrderId,
        name: 'Masternova',
        description,
        prefill,
        handler: (r) =>
          resolve({
            kind: 'paid',
            proof: { paymentId: r.razorpay_payment_id, signature: r.razorpay_signature },
          }),
        modal: { ondismiss: () => resolve({ kind: 'dismissed' }) },
      });
      // a failed attempt keeps the modal open for a retry; the order learns it from the webhook
      checkout.on('payment.failed', () => undefined);
      checkout.open();
    });
  }

  private load(): Promise<RazorpayConstructor> {
    const window = this.document.defaultView as
      (Window & { Razorpay?: RazorpayConstructor }) | null;
    if (window?.Razorpay) {
      return Promise.resolve(window.Razorpay);
    }
    this.loading ??= new Promise<RazorpayConstructor>((resolve, reject) => {
      const script = this.document.createElement('script');
      script.src = RAZORPAY_SCRIPT;
      script.async = true;
      // either way a failed load is forgotten, so a later click tries again (flaky network, a
      // blocker that let the file through but stopped it running)
      const failed = (message: string) => {
        this.loading = null;
        script.remove();
        reject(new Error(message));
      };
      script.onload = () =>
        window?.Razorpay ? resolve(window.Razorpay) : failed('Razorpay did not load');
      script.onerror = () => failed('Razorpay could not be loaded');
      this.document.head.appendChild(script);
    });
    return this.loading;
  }
}
