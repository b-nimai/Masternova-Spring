import { OrderStatus } from '../../core/api/commerce-api';

/**
 * Copy for commerce's problem codes (API conventions §1: branch on `code`, never on `detail`).
 * Unknown codes fall back to the server's `detail`, then to a generic sentence.
 */
export const COMMERCE_PROBLEMS: Record<string, string> = {
  COUPON_NOT_FOUND: "That coupon code doesn't exist.",
  COUPON_INACTIVE: 'That coupon is no longer offered.',
  COUPON_NOT_STARTED: "That coupon isn't valid yet.",
  COUPON_EXPIRED: 'That coupon has expired.',
  COUPON_EXHAUSTED: 'That coupon has been used up.',
  COUPON_NOT_APPLICABLE: "That coupon is for a course that isn't in your cart.",
  COUPON_MIN_SUBTOTAL: "Your cart is below that coupon's minimum.",
  COURSE_ALREADY_OWNED: 'You already have one of these courses. Remove it to continue.',
  COURSE_NOT_FOR_SALE: "One of these courses isn't on sale any more. Remove it to continue.",
  COURSE_IS_FREE: 'One of these courses is free: remove it and just start learning.',
  CART_TOO_LARGE: 'Your cart has too many courses.',
  CART_EMPTY: 'Your cart is empty.',
  PAYMENT_PROVIDER_UNAVAILABLE: 'Payments are temporarily unavailable. Try again in a moment.',
  PAYMENT_PROVIDER_ERROR: 'The payment provider refused this order. Nothing was charged.',
  NOT_FOUND: "One of these courses doesn't exist any more. Remove it to continue.",
};

/** Problems about the coupon belong under the coupon field; the rest above the button. */
export function isCouponProblem(code: string | undefined): boolean {
  return code?.startsWith('COUPON_') ?? false;
}

export const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Awaiting payment',
  FAILED: 'Payment failed',
  PAID: 'Paid',
  REFUNDED: 'Refunded',
};
