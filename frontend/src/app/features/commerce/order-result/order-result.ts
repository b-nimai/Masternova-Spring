import { DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { switchMap, take, takeWhile, timer } from 'rxjs';
import { CommerceApi } from '../../../core/api/commerce-api';
import { MoneyPipe } from '../../../shared/money-pipe';
import { STATUS_LABELS } from '../commerce-messages';

/** How often, and how many times, a PENDING order is asked again (2 s × 15 = 30 s). */
export const POLL_MS = 2000;
export const POLL_TIMES = 15;

/**
 * /orders/:id — the payment result, and the page the order history links to.
 *
 * ⭐ POLLING while PENDING: if the browser's confirm never arrived (closed tab, dropped network),
 *    the webhook pays the order seconds later. The resource is a STREAM — `timer` → GET →
 *    `takeWhile(PENDING, inclusive)` — so the page updates by itself and stops at the first final
 *    state, or after 30 s.
 */
@Component({
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
  ],
  selector: 'app-order-result',
  styleUrl: './order-result.scss',
  templateUrl: './order-result.html',
})
export class OrderResult {
  private readonly api = inject(CommerceApi);

  /** `:id` route param, bound to the input (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly labels = STATUS_LABELS;

  protected readonly order = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) =>
      timer(0, POLL_MS).pipe(
        take(POLL_TIMES + 1),
        switchMap(() => this.api.order(id)),
        takeWhile((o) => o.status === 'PENDING', true), // ⭐ inclusive: emit the final state
      ),
  });
}
