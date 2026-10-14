import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../../core/api/catalog-api';
import { AccessResponse, EntitlementApi } from '../../../core/api/entitlement-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { DENIAL_MESSAGES, isDenialReason } from '../../../shared/denial-messages';
import { DurationPipe } from '../../../shared/duration-pipe';
import { MoneyPipe } from '../../../shared/money-pipe';
import { AccessCta } from '../access-cta/access-cta';
import { LEVEL_LABELS } from '../catalog-labels';
import { Curriculum } from '../curriculum/curriculum';

/**
 * /courses/:slug — the course page.
 *
 * ⭐ `slug` is a ROUTE PARAM bound to an input (withComponentInputBinding): no ActivatedRoute,
 *    no paramMap subscription. Navigating from one course to another reuses this component and
 *    just changes the input.
 * ⭐ `rxResource` turns "load the course for this slug" into signals (value / isLoading / error)
 *    and re-runs when the slug changes, cancelling the previous request.
 * ⭐ The curriculum is `@defer (on viewport)`: its code and its rendering wait until it scrolls in.
 * ⭐ Phase 8: a SECOND resource, chained on the first — once the course (and so its id) is known,
 *    ask the entitlement engine what this viewer may watch. It re-runs when the viewer signs in or
 *    out. `?locked=<reason>` (the player's guard sent them back) becomes a banner.
 */
@Component({
  imports: [
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    DurationPipe,
    Curriculum,
    AccessCta,
  ],
  selector: 'app-course-detail',
  styleUrl: './course-detail.scss',
  templateUrl: './course-detail.html',
})
export class CourseDetail {
  private readonly api = inject(CatalogApi);
  private readonly entitlements = inject(EntitlementApi);
  private readonly auth = inject(AuthStore);

  readonly slug = input.required<string>();
  /** `?locked=<reason>`: the player's guard sent the viewer back here (query params bind too). */
  readonly locked = input<string | undefined>();

  protected readonly course = rxResource({
    params: () => this.slug(),
    stream: ({ params: slug }) => this.api.course(slug),
  });

  /** ⭐ `undefined` params keep the resource idle until the course has loaded. */
  protected readonly access = rxResource({
    params: () => {
      const id = this.course.value()?.id;
      return id ? { id, signedIn: this.auth.isAuthenticated() } : undefined;
    },
    stream: ({ params }) => this.entitlements.courseAccess(params.id),
  });

  protected readonly lectureAccess = computed<ReadonlyMap<string, AccessResponse> | null>(() => {
    const access = this.access.value();
    return access ? new Map(access.lectures.map((l) => [l.lectureId, l.access])) : null;
  });

  /** Where "Start learning" goes: the first video lecture this viewer may open. */
  protected readonly firstLectureId = computed(() => {
    const open = this.lectureAccess();
    const lectures = this.course.value()?.sections.flatMap((s) => s.lectures) ?? [];
    return (
      lectures.find((l) => l.kind === 'VIDEO' && open?.get(l.id)?.kind === 'ALLOWED')?.id ?? null
    );
  });

  /** The banner for `?locked=` — only for a reason we know (it's a url anyone can edit). */
  protected readonly lockedMessage = computed(() => {
    const reason = this.locked();
    return isDenialReason(reason) ? DENIAL_MESSAGES[reason] : null;
  });

  /** A 404 is "this course doesn't exist (for you)" — not an outage. */
  protected readonly notFound = computed(() => {
    const error = this.course.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });

  protected readonly levelLabels = LEVEL_LABELS;
}
