import { Component, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AccessResponse } from '../../../core/api/entitlement-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { DENIAL_MESSAGES } from '../../../shared/denial-messages';

/**
 * The course page's call to action, chosen by the entitlement engine's answer (API conventions
 * §10): start learning, sign in, buy, renew, contact support, or "no longer available".
 *
 * ⭐ A nested `@switch` over a discriminated union: the outer one on `kind`, the inner one on the
 *    denial `reason` — each branch can only read the fields its shape has, and the template
 *    compiler checks it.
 */
@Component({
  imports: [MatButtonModule, MatIconModule, RouterLink],
  selector: 'app-access-cta',
  styleUrl: './access-cta.scss',
  templateUrl: './access-cta.html',
})
export class AccessCta {
  protected readonly auth = inject(AuthStore);

  readonly access = input.required<AccessResponse>();
  readonly slug = input.required<string>();
  /** The first video lecture, where "Start learning" goes; null when there's nothing to play. */
  readonly firstLectureId = input<string | null>(null);
  /** The check itself failed (ACCESS_CHECK_FAILED): the page reloads the access. */
  readonly retry = output<void>();

  protected readonly messages = DENIAL_MESSAGES;
}
