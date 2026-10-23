import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { CommerceApi } from '../../../core/api/commerce-api';
import { asProblem, Problem } from '../../../core/api/problem';
import { CartStore } from '../../../core/commerce/cart-store';
import { coverFor } from '../../../shared/course-cover';
import { MoneyPipe } from '../../../shared/money-pipe';
import { CheckoutFlow } from '../checkout-flow';
import { COMMERCE_PROBLEMS, isCouponProblem } from '../commerce-messages';
import { Haptics } from '../../../core/haptics';

/**
 * /cart — what's in the cart, priced by the SERVER, with a coupon field and the checkout button.
 *
 * ⭐ The quote is an `rxResource` over (cart items, applied coupon): adding, removing or applying a
 *    coupon re-prices, and a stale request is cancelled. The numbers on screen are always the
 *    server's; the cart's own prices only show while the first quote loads.
 * ⭐ A coupon is applied by quoting WITH it first: a bad code shows its reason under the field and
 *    leaves the current prices alone.
 */
@Component({
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MoneyPipe,
  ],
  selector: 'app-cart',
  styleUrl: './cart.scss',
  templateUrl: './cart.html',
})
export class Cart {
  protected readonly cover = coverFor;
  protected readonly cart = inject(CartStore);
  private readonly haptics = inject(Haptics);
  private readonly api = inject(CommerceApi);
  private readonly flow = inject(CheckoutFlow);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** ⭐ A FormGroup, not a lone control: `(ngSubmit)` comes from the `[formGroup]` directive. */
  protected readonly couponForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
  });
  protected readonly appliedCoupon = signal<string | null>(null);
  protected readonly couponError = signal<string | null>(null);
  protected readonly applying = signal(false);
  protected readonly checkingOut = signal(false);
  protected readonly checkoutError = signal<string | null>(null);

  /** ⭐ `undefined` params (an empty cart) keep the resource idle: nothing to price. */
  protected readonly quote = rxResource({
    params: () => {
      const ids = this.cart.courseIds();
      return ids.length ? { ids, coupon: this.appliedCoupon() } : undefined;
    },
    stream: ({ params }) => this.api.quote(params.ids, params.coupon),
  });

  /** Why the cart can't be priced (a course already owned, no longer on sale, …). */
  protected readonly quoteProblem = computed(() => {
    const error = this.quote.error();
    return error ? message(asProblem(error)) : null;
  });

  protected remove(courseId: string): void {
    this.cart.remove(courseId);
    this.haptics.tap();
    this.checkoutError.set(null);
  }

  protected applyCoupon(): void {
    const code = this.couponForm.controls.code.value.trim();
    if (!code || this.couponForm.invalid) {
      return;
    }
    this.applying.set(true);
    this.couponError.set(null);
    this.api.quote(this.cart.courseIds(), code).subscribe({
      next: (q) => {
        this.applying.set(false);
        this.haptics.success();
        this.appliedCoupon.set(q.couponCode); // the resource re-prices with it
      },
      error: (e: unknown) => {
        this.applying.set(false);
        this.haptics.error();
        const problem = asProblem(e);
        this.couponError.set(
          isCouponProblem(problem?.code) ? message(problem) : "We couldn't check that code.",
        );
      },
    });
  }

  protected removeCoupon(): void {
    this.appliedCoupon.set(null);
    this.couponForm.reset();
    this.couponError.set(null);
  }

  protected async checkout(): Promise<void> {
    this.checkingOut.set(true);
    this.checkoutError.set(null);
    try {
      const outcome = await this.flow.checkout(this.appliedCoupon());
      if (outcome.kind === 'done') {
        await this.router.navigate(['/orders', outcome.order.id]);
      } else {
        this.snackBar.open('Payment cancelled. Nothing was charged.', 'OK', { duration: 5000 });
      }
    } catch (e: unknown) {
      this.checkoutError.set(message(asProblem(e)));
    } finally {
      this.checkingOut.set(false);
    }
  }
}

function message(problem: Problem | null): string {
  return (
    (problem?.code && COMMERCE_PROBLEMS[problem.code]) ||
    problem?.detail ||
    'Something went wrong. Please try again.'
  );
}
