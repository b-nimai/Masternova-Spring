import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CommerceApi } from './commerce-api';

describe('CommerceApi', () => {
  let api: CommerceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CommerceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends course ids and a code to quote — never an amount', () => {
    api.quote(['c1', 'c2'], 'SAVE20').subscribe();
    const req = http.expectOne('/api/v1/cart/quote');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ courseIds: ['c1', 'c2'], couponCode: 'SAVE20' });
    req.flush({});
  });

  it('checks out with an Idempotency-Key', () => {
    api.checkout(['c1'], null, 'key-1').subscribe();
    const req = http.expectOne('/api/v1/checkout');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
    expect(req.request.body).toEqual({ courseIds: ['c1'], couponCode: null });
    req.flush({});
  });

  it('confirms, reads orders and simulates a fake payment', () => {
    api.confirm('o1', { paymentId: 'pay_1', signature: 'sig' }).subscribe();
    expect(http.expectOne('/api/v1/orders/o1/confirm').request.body).toEqual({
      paymentId: 'pay_1',
      signature: 'sig',
    });
    api.orders().subscribe();
    http.expectOne('/api/v1/orders').flush([]);
    api.order('o1').subscribe();
    http.expectOne('/api/v1/orders/o1').flush({});
    api.simulatePayment('o1').subscribe();
    expect(http.expectOne('/api/v1/dev/fake-payments/o1').request.method).toBe('POST');
  });
});
