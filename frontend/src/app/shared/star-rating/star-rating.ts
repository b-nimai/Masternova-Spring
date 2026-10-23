import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

type Star = 'star' | 'star_half' | 'empty';

/**
 * The Figma's rating row: "4.5 ★★★★☆ (12)". Halves round to the nearest half star. Screen readers
 * get one sentence instead of five icons.
 */
@Component({
  selector: 'app-star-rating',
  imports: [DecimalPipe, MatIconModule],
  templateUrl: './star-rating.html',
  styleUrl: './star-rating.scss',
})
export class StarRating {
  /** 0–5. */
  readonly value = input.required<number>();
  /** How many ratings; left out, no "(n)". */
  readonly count = input<number>();

  protected readonly stars = computed<Star[]>(() => {
    const halves = Math.round(this.value() * 2);
    return [1, 2, 3, 4, 5].map((n) =>
      halves >= n * 2 ? 'star' : halves === n * 2 - 1 ? 'star_half' : 'empty',
    );
  });

  protected readonly label = computed(() => {
    const count = this.count();
    const rated = `Rated ${this.value().toFixed(1)} out of 5`;
    return count === undefined
      ? rated
      : `${rated} by ${count} ${count === 1 ? 'learner' : 'learners'}`;
  });
}
