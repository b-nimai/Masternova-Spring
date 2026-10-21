import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, of, throwError } from 'rxjs';
import { EngagementApi, ReviewResponse } from '../../../core/api/engagement-api';
import { asProblem } from '../../../core/api/problem';

/** The server's limit on a review's text (`Review.MAX_BODY`). */
export const MAX_REVIEW_LENGTH = 2000;

/**
 * The course page's reviews (Phase 11.5): the star distribution, the newest reviews with "Show
 * more", and — for an enrolled learner — their own review to write, edit or delete.
 *
 * ⭐ Paging is ACCUMULATED client state: each "Show more" appends the next keyset page (the
 *    cursor is opaque, API conventions §2). A write resets to page one: the learner's review is
 *    now the newest.
 * ⭐ Whether the learner may review is decided by the SERVER (enrolled, not the teacher). The form
 *    is shown to enrolled viewers, and a 403 `OWN_COURSE` becomes a sentence, not a crash.
 */
@Component({
  selector: 'app-course-reviews',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './course-reviews.html',
  styleUrl: './course-reviews.scss',
})
export class CourseReviews {
  private readonly api = inject(EngagementApi);

  readonly courseId = input.required<string>();
  /** Enrolled (the course page's progress says so): offer the form. */
  readonly canReview = input(false);

  protected readonly stars = [1, 2, 3, 4, 5];
  protected readonly maxLength = MAX_REVIEW_LENGTH;

  protected readonly summary = rxResource({
    params: () => this.courseId(),
    stream: ({ params: id }) => this.api.ratingSummary(id),
  });

  /** The bars, 5★ first, as a share of all ratings. */
  protected readonly distribution = computed(() => {
    const s = this.summary.value();
    return [5, 4, 3, 2, 1].map((star) => ({
      star,
      count: s?.stars[star - 1] ?? 0,
      percent: s && s.count > 0 ? Math.round((100 * s.stars[star - 1]) / s.count) : 0,
    }));
  });

  protected readonly reviews = signal<ReviewResponse[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly loadingMore = signal(false);

  /** The learner's own review: null = none yet (a 404 is an answer, not an error). */
  protected readonly mine = rxResource({
    params: () => (this.canReview() ? this.courseId() : undefined),
    stream: ({ params: id }) =>
      this.api
        .myReview(id)
        .pipe(
          catchError((e: unknown) =>
            e instanceof HttpErrorResponse && e.status === 404 ? of(null) : throwError(() => e),
          ),
        ),
  });

  protected readonly draftRating = signal(0);
  protected readonly draftBody = signal('');
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  constructor() {
    // a new course (the page navigated): start the list over
    effect(() => {
      const id = this.courseId();
      untracked(() => this.loadFirstPage(id));
    });
    // the draft starts from the saved review, whenever it (re)loads
    effect(() => {
      const review = this.mine.value();
      untracked(() => {
        this.draftRating.set(review?.rating ?? 0);
        this.draftBody.set(review?.body ?? '');
      });
    });
  }

  protected showMore(): void {
    const cursor = this.nextCursor();
    if (!cursor || this.loadingMore()) {
      return;
    }
    this.loadingMore.set(true);
    this.api.reviews(this.courseId(), cursor).subscribe({
      next: (page) => {
        this.reviews.update((list) => [...list, ...page.items]);
        this.nextCursor.set(page.nextCursor);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  protected save(): void {
    if (this.draftRating() < 1 || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .writeReview(this.courseId(), { rating: this.draftRating(), body: this.draftBody().trim() })
      .subscribe({
        next: (review) => this.afterWrite(review),
        error: (e: unknown) => this.failed(e),
      });
  }

  protected remove(): void {
    this.saving.set(true);
    this.api.deleteReview(this.courseId()).subscribe({
      next: () => this.afterWrite(null),
      error: (e: unknown) => this.failed(e),
    });
  }

  private afterWrite(review: ReviewResponse | null): void {
    this.saving.set(false);
    this.mine.set(review);
    this.summary.reload();
    this.loadFirstPage(this.courseId());
  }

  private failed(e: unknown): void {
    this.saving.set(false);
    const reason = asProblem(e)?.reason;
    this.formError.set(
      reason === 'OWN_COURSE'
        ? "You teach this course, so you can't review it."
        : reason === 'NOT_ENROLLED'
          ? 'Enroll in this course to review it.'
          : "We couldn't save your review. Try again.",
    );
  }

  private loadFirstPage(courseId: string): void {
    this.api.reviews(courseId, null).subscribe({
      next: (page) => {
        this.reviews.set(page.items);
        this.nextCursor.set(page.nextCursor);
      },
      error: () => {
        this.reviews.set([]);
        this.nextCursor.set(null);
      },
    });
  }
}
