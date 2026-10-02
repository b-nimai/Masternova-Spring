import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Title } from '@angular/platform-browser';
import { CartStore } from '../cart-store';
import { CourseCatalog } from '../course-catalog';
import { CourseCard } from '../course-card/course-card';
import { searchStates, SearchState } from '../course-search';
import { SortDirection, SortToggle } from '../sort-toggle/sort-toggle';

/**
 * The Angular essentials playground — a CONTAINER component: it owns state (signals), talks to
 * services, and hands data to presentational children. Study note:
 * patterns/angular/01-angular-essentials.md.
 */
@Component({
  selector: 'app-playground',
  imports: [
    CourseCard,
    SortToggle,
    CurrencyPipe,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatButtonModule,
  ],
  templateUrl: './playground.html',
  styleUrl: './playground.scss',
})
export class Playground {
  private readonly catalog = inject(CourseCatalog); // ⭐ inject(): DI without a constructor
  protected readonly cart = inject(CartStore);

  // ⭐ signal(): a writable reactive value. The template re-renders what reads it when it changes.
  protected readonly query = signal('');
  protected readonly sort = signal<SortDirection>('asc');

  // ⭐ signal → Observable (toObservable) → RxJS operators → back to a signal (toSignal).
  //    RxJS is the right tool for TIME (debounce, cancel); signals for STATE.
  protected readonly state = toSignal(
    searchStates(toObservable(this.query), (term) => this.catalog.search(term)),
    { initialValue: { kind: 'idle' } as SearchState },
  );

  // ⭐ computed(): derived from other signals; recomputes only when state() or sort() change.
  protected readonly results = computed(() => {
    const state = this.state();
    if (state.kind !== 'ok') {
      return [];
    }
    const direction = this.sort() === 'asc' ? 1 : -1;
    return [...state.items].sort((a, b) => (a.priceMinor - b.priceMinor) * direction);
  });

  constructor() {
    const title = inject(Title);
    // ⭐ effect(): for SIDE EFFECTS outside Angular's templates (here the browser tab title).
    //    Re-runs whenever a signal it read changes. Not for deriving state — that's computed().
    effect(() => {
      const count = this.cart.count();
      title.setTitle(count > 0 ? `Masternova (${count} in cart)` : 'Masternova');
    });
  }

  protected onSearchInput(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
}
