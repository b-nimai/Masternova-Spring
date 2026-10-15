import { Component, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { AccessResponse } from '../../../core/api/entitlement-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { CartItem, CartStore } from '../../../core/commerce/cart-store';
import { DENIAL_MESSAGES } from '../../../shared/denial-messages';

/**
 * The course page's call to action, chosen by the entitlement engine's answer (API conventions
 * §10): start learning, sign in, buy, renew, contact support, or "no longer available".
 *
 * ⭐ A nested `@switch` over a discriminated union: the outer one on `kind`, the inner one on the
 *    denial `reason` — each branch can only read the fields its shape has, and the template
 *    compiler checks it.
 * ⭐ Phase 9: "Buy now" and "Renew access" put the course in the cart (and go there); a course
 *    already in the cart offers "Go to cart" instead.
 */
@Component({
  imports: [MatButtonModule, MatIconModule, RouterLink],
  selector: 'app-access-cta',
  styleUrl: './access-cta.scss',
  templateUrl: './access-cta.html',
})
export class AccessCta {
  protected readonly auth = inject(AuthStore);
  protected readonly cart = inject(CartStore);
  private readonly router = inject(Router);

  readonly access = input.required<AccessResponse>();
  readonly slug = input.required<string>();
  /** The first video lecture, where "Start learning" goes; null when there's nothing to play. */
  readonly firstLectureId = input<string | null>(null);
  /** What the cart needs to show this course; null when it can't be bought (no price yet). */
  readonly offer = input<CartItem | null>(null);
  /** The check itself failed (ACCESS_CHECK_FAILED): the page reloads the access. */
  readonly retry = output<void>();

  protected readonly messages = DENIAL_MESSAGES;

  protected addToCart(goToCart: boolean): void {
    const offer = this.offer();
    if (offer) {
      this.cart.add(offer);
    }
    if (goToCart) {
      void this.router.navigate(['/cart']);
    }
  }
}
