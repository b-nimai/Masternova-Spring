import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../../core/api/catalog-api';
import { DurationPipe } from '../../../shared/duration-pipe';
import { MoneyPipe } from '../../../shared/money-pipe';
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
  ],
  selector: 'app-course-detail',
  styleUrl: './course-detail.scss',
  templateUrl: './course-detail.html',
})
export class CourseDetail {
  private readonly api = inject(CatalogApi);

  readonly slug = input.required<string>();

  protected readonly course = rxResource({
    params: () => this.slug(),
    stream: ({ params: slug }) => this.api.course(slug),
  });

  /** A 404 is "this course doesn't exist (for you)" — not an outage. */
  protected readonly notFound = computed(() => {
    const error = this.course.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });

  protected readonly levelLabels = LEVEL_LABELS;
}
