import { OrderResponse, QuoteResponse } from '../app/core/api/commerce-api';
import { CartItem } from '../app/core/commerce/cart-store';

export const JAVA: CartItem = {
  courseId: 'c-java',
  slug: 'java',
  title: 'Java from zero',
  priceMinor: 49900,
  currency: 'INR',
};

export const SPRING: CartItem = {
  courseId: 'c-spring',
  slug: 'spring',
  title: 'Spring in practice',
  priceMinor: 79900,
  currency: 'INR',
};

export function quoteOf(
  items: CartItem[],
  discountMinor = 0,
  couponCode: string | null = null,
): QuoteResponse {
  const subtotal = items.reduce((sum, i) => sum + i.priceMinor, 0);
  return {
    lines: items.map((i) => ({
      courseId: i.courseId,
      slug: i.slug,
      title: i.title,
      priceMinor: i.priceMinor,
    })),
    subtotalMinor: subtotal,
    discountMinor,
    totalMinor: subtotal - discountMinor,
    currency: 'INR',
    couponCode,
  };
}

export function orderOf(
  status: OrderResponse['status'],
  items: CartItem[] = [JAVA],
): OrderResponse {
  const q = quoteOf(items);
  return {
    id: 'o-1',
    status,
    lines: q.lines,
    subtotalMinor: q.subtotalMinor,
    discountMinor: 0,
    totalMinor: q.totalMinor,
    currency: 'INR',
    couponCode: null,
    createdAt: '2026-10-14T10:00:00Z',
    paidAt: status === 'PAID' || status === 'REFUNDED' ? '2026-10-14T10:01:00Z' : null,
    refundedAt: status === 'REFUNDED' ? '2026-10-15T10:00:00Z' : null,
  };
}
