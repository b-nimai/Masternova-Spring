import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { PaymentDetails, CommerceApi } from '../../../core/api/commerce-api';
import { WidgetResult } from '../../../core/commerce/razorpay-checkout';
import { MoneyPipe } from '../../../shared/money-pipe';

/** What the dialog shows: the order and the amount the provider would charge. */
export interface FakeCheckoutData {
  orderId: string;
  payment: PaymentDetails;
  titles: string[];
}

/**
 * DEV ONLY: stands in for Razorpay's modal when the server runs the FAKE provider (no keys in
 * dev, e2e, CI). "Pay" asks the dev endpoint for what Razorpay would hand back — a payment id
 * and its signature — and closes with it, exactly like Razorpay's `handler`.
 */
@Component({
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MoneyPipe],
  selector: 'app-fake-checkout-dialog',
  styleUrl: './fake-checkout-dialog.scss',
  templateUrl: './fake-checkout-dialog.html',
})
export class FakeCheckoutDialog {
  protected readonly data = inject<FakeCheckoutData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<FakeCheckoutDialog, WidgetResult>>(MatDialogRef);
  private readonly api = inject(CommerceApi);
  protected readonly paying = signal(false);
  protected readonly failed = signal(false);

  protected pay(): void {
    this.paying.set(true);
    this.failed.set(false);
    this.api.simulatePayment(this.data.orderId).subscribe({
      next: (proof) => this.ref.close({ kind: 'paid', proof }),
      error: () => {
        this.paying.set(false);
        this.failed.set(true);
      },
    });
  }

  protected cancel(): void {
    this.ref.close({ kind: 'dismissed' });
  }
}
