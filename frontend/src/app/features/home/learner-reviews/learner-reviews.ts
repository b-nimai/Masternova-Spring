import { Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';
import { CourseSummary } from '../../../core/api/catalog-api';
import { EngagementApi } from '../../../core/api/engagement-api';

/**
 * "Reviews from other learners" (Figma frame 46): the latest reviews of one course. The API has
 * no site-wide review feed, so the home page passes its best-rated course.
 *
 * ⭐ A resource CHAINED on an input: `params` is undefined until the course arrives (or when it has
 * no ratings), and an undefined params leaves the resource idle: no request, no value.
 */
@Component({
  selector: 'app-learner-reviews',
  imports: [MatIconModule],
  templateUrl: './learner-reviews.html',
  styleUrl: './learner-reviews.scss',
})
export class LearnerReviews {
  private readonly api = inject(EngagementApi);
  readonly course = input<CourseSummary>();

  protected readonly reviews = rxResource({
    params: () => {
      const course = this.course();
      return course && course.ratingCount > 0 ? course.id : undefined;
    },
    stream: ({ params: courseId }) =>
      this.api.reviews(courseId, null, 4).pipe(map((page) => page.items)),
  });

  protected readonly stars = [1, 2, 3, 4, 5];
}
