import { computed, Service, signal } from '@angular/core';
import { CourseSummary } from './course-catalog';

/**
 * A tiny signal STORE: state + derived values + the only methods allowed to change it. The
 * Angular version of note 06's encapsulated Cart (and of a React context + useReducer).
 *
 * ⭐ The writable signal is PRIVATE; the outside world gets a read-only view and named actions.
 */
@Service()
export class CartStore {
  private readonly items = signal<readonly CourseSummary[]>([]);

  /** Read-only view: components can read it in templates but can't `.set()` it. */
  readonly courses = this.items.asReadonly();

  // ⭐ computed: derived state, recalculated only when `items` changes, cached otherwise
  readonly count = computed(() => this.items().length);
  readonly totalMinor = computed(() => this.items().reduce((sum, c) => sum + c.priceMinor, 0));

  has(courseId: string): boolean {
    return this.items().some((c) => c.id === courseId); // reading a signal → reactive in templates
  }

  add(course: CourseSummary): void {
    if (this.has(course.id)) {
      return; // invariant: each course once (same rule as the Java Cart)
    }
    // ⭐ IMMUTABLE update: a NEW array. Signals compare by reference — mutating the old array in
    //    place (push) would not notify anyone.
    this.items.update((list) => [...list, course]);
  }

  remove(courseId: string): void {
    this.items.update((list) => list.filter((c) => c.id !== courseId));
  }

  clear(): void {
    this.items.set([]);
  }
}
