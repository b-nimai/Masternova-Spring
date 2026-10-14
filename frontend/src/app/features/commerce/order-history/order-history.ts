import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { CommerceApi, OrderResponse } from '../../../core/api/commerce-api';
import { MoneyPipe } from '../../../shared/money-pipe';
import { STATUS_LABELS } from '../commerce-messages';

/** /account/orders — every order, newest first; each row opens its result page. */
@Component({
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MoneyPipe,
  ],
  selector: 'app-order-history',
  styleUrl: './order-history.scss',
  templateUrl: './order-history.html',
})
export class OrderHistory {
  private readonly api = inject(CommerceApi);

  protected readonly columns = ['date', 'courses', 'total', 'status'];
  protected readonly orders = rxResource({ stream: () => this.api.orders() });

  /** ⭐ Typed helpers: `*matCellDef="let o"` rows are `any` in the template. */
  protected label(order: OrderResponse): string {
    return STATUS_LABELS[order.status];
  }

  protected titles(order: OrderResponse): string {
    return order.lines.map((l) => l.title).join(', ');
  }
}
