import { inject, Service } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import {
  CheckoutResponse,
  CommerceApi,
  OrderResponse,
  PaymentDetails,
} from '../../core/api/commerce-api';
import { AuthStore } from '../../core/auth/auth-store';
import { CartStore } from '../../core/commerce/cart-store';
import { RazorpayCheckout, WidgetResult } from '../../core/commerce/razorpay-checkout';
import { FakeCheckoutData, FakeCheckoutDialog } from './fake-checkout-dialog/fake-checkout-dialog';

/** How checking out ended, for the cart page to act on. */
export type CheckoutOutcome =
  | { kind: 'done'; order: OrderResponse } // paid, free, or paid-but-unconfirmed: the result page
  | { kind: 'dismissed' }; // the buyer closed the widget: stay on the cart

/**
 * Checkout from the browser's side (commerce LLD §5): place the order, open the provider's
 * widget, confirm the payment. The cart page calls one method.
 *
 * ⭐ The widget is chosen by the provider the SERVER names (`payment.provider`), mirroring the
 *    backend's PaymentGateway Strategy: Razorpay Checkout, or the dev simulator for FAKE. The page
 *    never knows which one ran.
 * ⭐ If the tab closes after paying, nothing is lost: the webhook pays the order, and the order
 *    history shows it. That's why the result page polls while an order is PENDING.
 */
@Service()
export class CheckoutFlow {
  private readonly api = inject(CommerceApi);
  private readonly cart = inject(CartStore);
  private readonly auth = inject(AuthStore);
  private readonly razorpay = inject(RazorpayCheckout);
  private readonly dialog = inject(MatDialog);

  async checkout(couponCode: string | null): Promise<CheckoutOutcome> {
    const courseIds = this.cart.courseIds();
    // one key per attempt: a network retry of THIS request replays it, a new click is a new order
    const placed = await firstValueFrom(
      this.api.checkout(courseIds, couponCode, crypto.randomUUID()),
    );
    if (!placed.payment) {
      return this.bought(placed.order); // a 100% coupon: already PAID
    }
    const result = await this.openWidget(placed, placed.payment);
    if (result.kind === 'dismissed') {
      return { kind: 'dismissed' };
    }
    try {
      return this.bought(await firstValueFrom(this.api.confirm(placed.order.id, result.proof)));
    } catch {
      // ⭐ the buyer HAS paid: never send them back to a live Checkout button (a second order, a
      //    second charge). The webhook pays the order; the result page polls until it does.
      return this.bought(placed.order);
    }
  }

  private openWidget(placed: CheckoutResponse, payment: PaymentDetails): Promise<WidgetResult> {
    const titles = placed.order.lines.map((l) => l.title);
    switch (payment.provider) {
      case 'RAZORPAY': {
        const user = this.auth.user();
        return this.razorpay.pay(
          payment,
          titles.length === 1 ? titles[0] : `${titles.length} courses`,
          { name: user?.displayName, email: user?.email },
        );
      }
      case 'FAKE':
        return firstValueFrom(
          this.dialog
            .open<FakeCheckoutDialog, FakeCheckoutData, WidgetResult>(FakeCheckoutDialog, {
              data: { orderId: placed.order.id, payment, titles },
              width: '420px',
            })
            .afterClosed(),
        ).then((r) => r ?? { kind: 'dismissed' });
    }
  }

  private bought(order: OrderResponse): CheckoutOutcome {
    this.cart.removeAll(order.lines.map((l) => l.courseId));
    return { kind: 'done', order };
  }
}
