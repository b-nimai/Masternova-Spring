import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { CourseSummary } from '../../../core/api/catalog-api';
import { DurationPipe } from '../../../shared/duration-pipe';
import { MoneyPipe } from '../../../shared/money-pipe';
import { LEVEL_LABELS } from '../catalog-labels';

/** One course in a list — purely presentational: data in through an input, links out. */
@Component({
  imports: [DecimalPipe, RouterLink, MatIconModule, MoneyPipe, DurationPipe],
  selector: 'app-course-card',
  styleUrl: './course-card.scss',
  templateUrl: './course-card.html',
})
export class CourseCard {
  readonly course = input.required<CourseSummary>();
  protected readonly levelLabels = LEVEL_LABELS;
}
