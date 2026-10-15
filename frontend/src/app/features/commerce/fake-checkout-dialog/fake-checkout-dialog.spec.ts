import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { CommerceApi } from '../../../core/api/commerce-api';
import { FakeCheckoutDialog } from './fake-checkout-dialog';

describe('FakeCheckoutDialog', () => {
  const close = vi.fn();
  const api = { simulatePayment: vi.fn() };

  function open() {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      imports: [FakeCheckoutDialog],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: CommerceApi, useValue: api },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            orderId: 'o-1',
            titles: ['Java from zero'],
            payment: {
              provider: 'FAKE',
              publicKey: 'fake_key',
              providerOrderId: 'order_fake_1',
              amountMinor: 49900,
              currency: 'INR',
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(FakeCheckoutDialog);
    fixture.detectChanges();
    return fixture;
  }

  it('plays Razorpay’s handler: closes with the proof the dev endpoint signed', () => {
    const fixture = open();
    api.simulatePayment.mockReturnValue(of({ paymentId: 'pay_1', signature: 'sig' }));
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="fake-pay"]')!
      .click();
    expect(api.simulatePayment).toHaveBeenCalledWith('o-1');
    expect(close).toHaveBeenCalledWith({
      kind: 'paid',
      proof: { paymentId: 'pay_1', signature: 'sig' },
    });
  });

  it('stays open with an error when the simulation fails', () => {
    const fixture = open();
    api.simulatePayment.mockReturnValue(throwError(() => new Error('down')));
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('[data-testid="fake-pay"]')!.click();
    fixture.detectChanges();
    expect(close).not.toHaveBeenCalled();
    expect(el.querySelector('[data-testid="fake-error"]')).not.toBeNull();
  });
});
