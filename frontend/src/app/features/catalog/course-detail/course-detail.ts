import { DatePipe, ViewportScroller } from '@angular/common';
import {
  afterNextRender,
  Component,
  computed,
  effect,
  inject,
  Injector,
  input,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogApi } from '../../../core/api/catalog-api';
import { AccessResponse, EntitlementApi } from '../../../core/api/entitlement-api';
import { LearningApi } from '../../../core/api/learning-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { CartItem } from '../../../core/commerce/cart-store';
import { isNotFound } from '../../../core/api/problem';
import { coverFor } from '../../../shared/course-cover';
import { DENIAL_MESSAGES, isDenialReason } from '../../../shared/denial-messages';
import { DurationPipe } from '../../../shared/duration-pipe';
import { MoneyPipe } from '../../../shared/money-pipe';
import { StarRating } from '../../../shared/star-rating/star-rating';
import { AccessCta } from '../access-cta/access-cta';
import { LEVEL_LABELS } from '../catalog-labels';
import { CourseReviews } from '../course-reviews/course-reviews';
import { Curriculum } from '../curriculum/curriculum';
import { Reveal } from '../../../shared/motion/reveal';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Haptics } from '../../../core/haptics';

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
 * ⭐ Phase 10: a THIRD resource, chained on the access: a signed-in viewer who may watch gets
 *    their progress (read through the buffer, so a lecture watched a second ago already counts):
 *    the call to action continues where they stopped, and the curriculum ticks what's done.
 * ⭐ Phase 11: reviews below the curriculum, deferred too; enrolled learners get the form.
 */
@Component({
  imports: [
    Skeleton,
    Reveal,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    DurationPipe,
    Curriculum,
    AccessCta,
    CourseReviews,
    StarRating,
  ],
  selector: 'app-course-detail',
  styleUrl: './course-detail.scss',
  templateUrl: './course-detail.html',
})
export class CourseDetail {
  private readonly api = inject(CatalogApi);
  private readonly entitlements = inject(EntitlementApi);
  private readonly auth = inject(AuthStore);
  private readonly learning = inject(LearningApi);

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

  /** Only worth asking when signed in and allowed in: everyone else has no progress. */
  protected readonly progress = rxResource({
    params: () => {
      const access = this.access.value();
      return this.auth.isAuthenticated() && access?.course.kind === 'ALLOWED'
        ? access.courseId
        : undefined;
    },
    stream: ({ params: courseId }) => this.learning.courseProgress(courseId),
  });

  protected readonly completed = computed<ReadonlySet<string>>(
    () => new Set(this.progress.hasValue() ? this.progress.value().completedLectureIds : []),
  );

  protected readonly enrolling = signal(false);
  private readonly haptics = inject(Haptics);

  /** Free enrollment (idempotent PUT), then the progress again: the button turns to "Start". */
  protected enroll(courseId: string): void {
    this.enrolling.set(true);
    this.learning.enroll(courseId).subscribe({
      next: () => {
        this.enrolling.set(false);
        this.haptics.success();
        this.progress.reload();
      },
      error: () => {
        this.enrolling.set(false);
        this.haptics.error();
        this.access.reload(); // refused (revoked, no longer free): the access says why
      },
    });
  }

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

  /**
   * What "Buy now" puts in the cart (Phase 9); null when the server wouldn't sell it — the same
   * rule as its COURSE_NOT_FOR_SALE / COURSE_IS_FREE: published, priced, and not free.
   */
  protected readonly offer = computed<CartItem | null>(() => {
    const c = this.course.value();
    return c && c.status === 'PUBLISHED' && c.priceSet && c.priceMinor > 0
      ? {
          courseId: c.id,
          slug: c.slug,
          title: c.title,
          priceMinor: c.priceMinor,
          currency: c.currency,
        }
      : null;
  });

  /** The banner for `?locked=` — only for a reason we know (it's a url anyone can edit). */
  protected readonly lockedMessage = computed(() => {
    const reason = this.locked();
    return isDenialReason(reason) ? DENIAL_MESSAGES[reason] : null;
  });

  /** A 404 is "this course doesn't exist (for you)" — not an outage. */
  protected readonly notFound = computed(() => {
    const error = this.course.error();
    return isNotFound(error);
  });

  protected readonly levelLabels = LEVEL_LABELS;

  protected readonly cover = computed(() => coverFor(this.course.value()?.id ?? ''));

  /** "en" → "English", in the viewer's own language (a computed: one formatter per course). */
  protected readonly languageName = computed(() => {
    const code = this.course.value()?.language ?? '';
    return new Intl.DisplayNames(undefined, { type: 'language' }).of(code) ?? code;
  });

  protected readonly shareState = signal<'idle' | 'copied' | 'failed'>('idle');

  private readonly fragment = toSignal(inject(ActivatedRoute).fragment);
  private readonly scroller = inject(ViewportScroller);
  private readonly injector = inject(Injector);

  constructor() {
    // `#reviews` (the player's "Add Review"): the anchor exists only once the course has
    // rendered, so the router can't scroll to it on arrival. Scroll once it's on the page.
    effect(() => {
      if (this.fragment() === 'reviews' && this.course.hasValue()) {
        afterNextRender(() => this.scroller.scrollToAnchor('reviews'), {
          injector: this.injector,
        });
      }
    });
  }

  /** The Figma's "Share": copies the page's address (refused: an insecure page, no permission). */
  protected share(): void {
    const copied = navigator.clipboard?.writeText(location.href) ?? Promise.reject();
    copied.then(
      () => this.shareState.set('copied'),
      () => this.shareState.set('failed'),
    );
  }
}
