import { CurrencyPipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { CourseSummary } from '../course-catalog';

/**
 * A PRESENTATIONAL component: data in through inputs, events out through outputs — no services.
 * (React: props in, callback props out.)
 */
@Component({
  selector: 'app-course-card',
  imports: [MatCardModule, MatButtonModule, CurrencyPipe],
  templateUrl: './course-card.html',
  styleUrl: './course-card.scss',
})
export class CourseCard {
  // ⭐ input(): a SIGNAL of what the parent passes. required = compile error if the parent forgets.
  readonly course = input.required<CourseSummary>();
  readonly inCart = input(false);

  // ⭐ output(): an event the parent listens to with (added)="…"
  readonly added = output<CourseSummary>();

  // derived from an input → recomputed whenever the parent passes a new course
  protected readonly priceRupees = computed(() => this.course().priceMinor / 100);
  protected readonly isFree = computed(() => this.course().priceMinor === 0);
}
