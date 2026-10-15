import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { JAVA, SPRING, orderOf } from '../../../../testing/commerce-fixtures';
import { OrderHistory } from './order-history';

describe('OrderHistory', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderHistory],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists orders with their courses, total and status, each linking to its page', async () => {
    const fixture = TestBed.createComponent(OrderHistory);
    TestBed.tick();
    http
      .expectOne('/api/v1/orders')
      .flush([orderOf('PAID', [JAVA, SPRING]), { ...orderOf('REFUNDED'), id: 'o-2' }]);
    await fixture.whenStable();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Java from zero, Spring in practice');
    expect(rows[0].textContent).toContain('1,298');
    expect(rows[0].textContent).toContain('Paid');
    expect(rows[1].querySelector('a')!.getAttribute('href')).toBe('/orders/o-2');
    expect(rows[1].textContent).toContain('Refunded');
  });

  it('says when there are none', async () => {
    const fixture = TestBed.createComponent(OrderHistory);
    TestBed.tick();
    http.expectOne('/api/v1/orders').flush([]);
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="no-orders"]'),
    ).not.toBeNull();
  });
});
