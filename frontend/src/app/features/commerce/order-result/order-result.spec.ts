import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { orderOf } from '../../../../testing/commerce-fixtures';
import { OrderResult, POLL_MS, POLL_TIMES } from './order-result';

describe('OrderResult', () => {
  let fixture: ComponentFixture<OrderResult>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers(); // the poll's timer; promises (microtasks) stay real
    await TestBed.configureTestingModule({
      imports: [OrderResult],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderResult);
    fixture.componentRef.setInput('id', 'o-1');
    TestBed.tick(); // the resource subscribes: the poll's timer starts
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  /** Advance the poll, answer its request, let the value land and render. */
  async function poll(status: Parameters<typeof orderOf>[0], ms = POLL_MS): Promise<HTMLElement> {
    vi.advanceTimersByTime(ms);
    http.expectOne('/api/v1/orders/o-1').flush(orderOf(status));
    await Promise.resolve();
    TestBed.tick();
    return fixture.nativeElement as HTMLElement;
  }

  it('polls a PENDING order until the webhook has paid it, then stops', async () => {
    expect(
      (await poll('PENDING', 0)).querySelector('[data-testid="result-pending"]'),
    ).not.toBeNull();
    await poll('PENDING');
    const el = await poll('PAID');

    expect(el.querySelector('[data-testid="result-paid"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="start-learning"]')!.getAttribute('href')).toBe(
      '/courses/java',
    );
    vi.advanceTimersByTime(POLL_MS * 5);
    http.expectNone('/api/v1/orders/o-1'); // ⭐ a final state ends the polling
  });

  it('shows a failed payment with the way back to the cart', async () => {
    const el = await poll('FAILED', 0);
    expect(el.querySelector('[data-testid="result-failed"] a')!.getAttribute('href')).toBe('/cart');
    vi.advanceTimersByTime(POLL_MS * 3);
    http.expectNone('/api/v1/orders/o-1');
  });

  it('gives up after 30 s and keeps saying it is waiting', async () => {
    let el = await poll('PENDING', 0);
    for (let i = 0; i < POLL_TIMES; i++) {
      el = await poll('PENDING');
    }
    vi.advanceTimersByTime(POLL_MS * 3);
    http.expectNone('/api/v1/orders/o-1');
    expect(el.querySelector('[data-testid="result-pending"]')).not.toBeNull();
  });
});
