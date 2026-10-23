import { Component, computed, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { AccessResponse } from '../../../core/api/entitlement-api';
import { CourseProgressResponse } from '../../../core/api/learning-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { CartItem, CartStore } from '../../../core/commerce/cart-store';
import { DENIAL_MESSAGES } from '../../../shared/denial-messages';
import { Haptics } from '../../../core/haptics';

/**
 * The course page's call to action, chosen by the entitlement engine's answer (API conventions
 * §10): start learning, sign in, buy, renew, contact support, or "no longer available".
 *
 * ⭐ A nested `@switch` over a discriminated union: the outer one on `kind`, the inner one on the
 *    denial `reason` — each branch can only read the fields its shape has, and the template
 *    compiler checks it.
 * ⭐ Phase 9: "Buy now" and "Renew access" put the course in the cart (and go there); a course
 *    already in the cart offers "Go to cart" instead.
 * ⭐ Phase 10: an enrolled learner's button goes where they stopped ("Continue learning", the
 *    server's `continueLectureId`), and a signed-in visitor of a free course can enroll — which
 *    is what puts it on "My Learning" and starts tracking progress.
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
  private readonly haptics = inject(Haptics);

  readonly access = input.required<AccessResponse>();
  readonly slug = input.required<string>();
  /** The first video lecture, where "Start learning" goes; null when there's nothing to play. */
  readonly firstLectureId = input<string | null>(null);
  /** What the cart needs to show this course; null when it can't be bought (no price yet). */
  readonly offer = input<CartItem | null>(null);
  /** The learner's progress in this course; null = unknown (signed out, loading, no access). */
  readonly progress = input<CourseProgressResponse | null>(null);
  /** A free enrollment is on its way: the button waits. */
  readonly enrolling = input(false);
  /** The check itself failed (ACCESS_CHECK_FAILED): the page reloads the access. */
  readonly retry = output<void>();
  /** "Enroll for free" was clicked: the page enrolls and reloads the progress. */
  readonly enroll = output<void>();

  /** Where the main button goes: where they stopped, else the first open video. */
  protected readonly lectureId = computed(
    () => this.progress()?.continueLectureId ?? this.firstLectureId(),
  );

  /** ⭐ One answer for the ALLOWED branch, so the template has no nested rules. */
  protected readonly step = computed<'ENROLL' | 'START' | 'CONTINUE' | 'REVIEW'>(() => {
    const p = this.progress();
    if (!p?.enrolled) {
      return p && this.auth.isAuthenticated() && this.isFree() ? 'ENROLL' : 'START';
    }
    if (p.continueLectureId === null) return 'REVIEW';
    const started =
      p.percentComplete > 0 ||
      p.continuePositionSeconds > 0 ||
      p.continueLectureId !== this.firstLectureId();
    return started ? 'CONTINUE' : 'START';
  });

  private isFree(): boolean {
    const a = this.access();
    return a.kind === 'ALLOWED' && a.grant === 'FREE_COURSE';
  }

  protected readonly messages = DENIAL_MESSAGES;

  protected addToCart(goToCart: boolean): void {
    const offer = this.offer();
    if (offer) {
      this.cart.add(offer);
      this.haptics.success();
    }
    if (goToCart) {
      void this.router.navigate(['/cart']);
    }
  }
}
