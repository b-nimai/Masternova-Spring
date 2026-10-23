import { Component, computed, input } from '@angular/core';

/** What is loading: a course card, a table row, or a page's header band. */
export type SkeletonShape = 'card' | 'row' | 'band';

/**
 * A shimmering placeholder in the shape of what is coming (instead of a spinner): the page keeps
 * its layout while it loads, so nothing jumps when the data lands. Screen readers hear one
 * "Loading…", not the blocks.
 */
@Component({
  selector: 'app-skeleton',
  host: { role: 'status', '[attr.aria-busy]': 'true' },
  templateUrl: './skeleton.html',
  styleUrl: './skeleton.scss',
})
export class Skeleton {
  readonly shape = input<SkeletonShape>('row');
  readonly count = input(1);

  protected readonly items = computed(() => Array.from({ length: this.count() }, (_, i) => i));
}
