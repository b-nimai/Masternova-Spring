import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CourseSummary } from '../../../core/api/catalog-api';
import { coverFor } from '../../../shared/course-cover';
import { DurationPipe } from '../../../shared/duration-pipe';
import { MoneyPipe } from '../../../shared/money-pipe';
import { StarRating } from '../../../shared/star-rating/star-rating';
import { LEVEL_LABELS } from '../catalog-labels';

/** One course in a list — purely presentational: data in through an input, links out. */
@Component({
  imports: [RouterLink, MoneyPipe, DurationPipe, StarRating],
  selector: 'app-course-card',
  styleUrl: './course-card.scss',
  templateUrl: './course-card.html',
})
export class CourseCard {
  readonly course = input.required<CourseSummary>();
  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly cover = computed(() => coverFor(this.course().id));
}
