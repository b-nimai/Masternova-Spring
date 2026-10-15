import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors `OrderLineResponse`: one course in a quote or an order, as priced. */
export interface OrderLineResponse {
  courseId: string;
  slug: string;
  title: string;
  priceMinor: number;
}

/** Mirrors `QuoteResponse`: a priced cart (API conventions §5: minor units + one currency). */
export interface QuoteResponse {
  lines: OrderLineResponse[];
  subtotalMinor: number;
  discountMinor: number;
  totalMinor: number;
  currency: string;
  couponCode: string | null;
}

/** Mirrors `OrderStatus`: forward-only (LLD §3). */
export type OrderStatus = 'PENDING' | 'FAILED' | 'PAID' | 'REFUNDED';

/** Mirrors `OrderResponse`. */
export interface OrderResponse {
  id: string;
  status: OrderStatus;
  lines: OrderLineResponse[];
  subtotalMinor: number;
  discountMinor: number;
  totalMinor: number;
  currency: string;
  couponCode: string | null;
  createdAt: string;
  paidAt: string | null;
  refundedAt: string | null;
}

/** Mirrors `CheckoutResponse.Payment`: what the provider's widget needs. */
export interface PaymentDetails {
  provider: 'RAZORPAY' | 'FAKE';
  publicKey: string;
  providerOrderId: string;
  amountMinor: number;
  currency: string;
}

/** Mirrors `CheckoutResponse`. `payment` is null for a free order: it is already PAID. */
export interface CheckoutResponse {
  order: OrderResponse;
  payment: PaymentDetails | null;
}

/** What a provider's checkout widget hands back after a successful payment. */
export interface PaymentProof {
  paymentId: string;
  signature: string;
}

/** Commerce: price the cart, check out, confirm the payment, read orders. */
@Service()
export class CommerceApi {
  private readonly http = inject(HttpClient);

  /** Safe (nothing stored): the server prices the cart. 422s carry a code (COUPON_EXPIRED, …). */
  quote(courseIds: readonly string[], couponCode: string | null): Observable<QuoteResponse> {
    return this.http.post<QuoteResponse>('/api/v1/cart/quote', { courseIds, couponCode });
  }

  /**
   * ⭐ Course ids and a code, never an amount: the server re-prices. The Idempotency-Key makes a
   * retried request replay the same order instead of opening a second one.
   */
  checkout(
    courseIds: readonly string[],
    couponCode: string | null,
    idempotencyKey: string,
  ): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(
      '/api/v1/checkout',
      { courseIds, couponCode },
      { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) },
    );
  }

  /** The browser's half of the race with the webhook: either one pays the order, once. */
  confirm(orderId: string, proof: PaymentProof): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`/api/v1/orders/${orderId}/confirm`, proof);
  }

  orders(): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>('/api/v1/orders');
  }

  order(id: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`/api/v1/orders/${id}`);
  }

  /** DEV ONLY (the FAKE provider): what Razorpay's modal would hand back. 404 with Razorpay. */
  simulatePayment(orderId: string): Observable<PaymentProof> {
    return this.http.post<PaymentProof>(`/api/v1/dev/fake-payments/${orderId}`, null);
  }
}
