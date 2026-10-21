import { Component, inject, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';
import { MIN_SUGGEST_LENGTH, SearchApi, SuggestionResponse } from '../../core/api/search-api';

/** How long typing must pause before the typeahead asks (ms). */
export const SUGGEST_DEBOUNCE_MS = 200;

/**
 * The toolbar's course search: a typeahead of titles, Enter for the full results page.
 *
 * ⭐ The keystroke pipeline, in RxJS because it's about TIME and CANCELLATION:
 *   valueChanges → debounceTime (wait for a pause) → distinctUntilChanged (same text, no call)
 *   → switchMap (a new text UNSUBSCRIBES from the previous request: HttpClient aborts it, so an
 *   old, slow answer can never overwrite a newer one) → toSignal at the boundary.
 * ⭐ A failed suggestion request becomes an empty list INSIDE the switchMap: a catchError outside
 *    would complete the whole stream, and the box would stop suggesting for good.
 */
@Component({
  selector: 'app-search-box',
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
  ],
  templateUrl: './search-box.html',
  styleUrl: './search-box.scss',
})
export class SearchBox {
  private readonly api = inject(SearchApi);
  private readonly router = inject(Router);

  /** After navigating (the shell closes a drawer on phones). */
  readonly searched = output<void>();

  /**
   * ⭐ Typed as what MatAutocomplete really writes into it: the text typed, or the OPTION'S VALUE
   *    (a whole SuggestionResponse) when one is picked. Typed as `string`, the pipeline below called
   *    `.trim()` on an object 200 ms after a pick, the stream died, and the box never suggested again.
   */
  protected readonly query = new FormControl<string | SuggestionResponse>('', {
    nonNullable: true,
  });

  protected readonly suggestions = toSignal(
    this.query.valueChanges.pipe(
      filter((value): value is string => typeof value === 'string'), // a pick isn't typing
      debounceTime(SUGGEST_DEBOUNCE_MS),
      map((text) => text.trim()),
      distinctUntilChanged(),
      switchMap((text) =>
        text.length < MIN_SUGGEST_LENGTH
          ? of<SuggestionResponse[]>([])
          : this.api.suggest(text).pipe(catchError(() => of<SuggestionResponse[]>([]))),
      ),
    ),
    { initialValue: [] as SuggestionResponse[] },
  );

  /** A suggestion opens its course page. */
  protected open(event: MatAutocompleteSelectedEvent): void {
    const suggestion = event.option.value as SuggestionResponse;
    this.query.setValue('', { emitEvent: false });
    void this.router.navigate(['/courses', suggestion.slug]);
    this.searched.emit();
  }

  /**
   * Enter: the ranked results page for the text as typed.
   *
   * ⭐ The NATIVE submit event: `ngSubmit` comes from NgForm (FormsModule) or `[formGroup]`, and
   *    this form has neither, so nothing prevented the browser's own submit, which reloaded the page.
   */
  protected submit(event: Event): void {
    event.preventDefault();
    const value = this.query.value;
    const q = typeof value === 'string' ? value.trim() : value.title;
    if (q) {
      void this.router.navigate(['/search'], { queryParams: { q } });
      this.searched.emit();
    }
  }

  /** The autocomplete shows the title, not "[object Object]", once one is picked. */
  protected readonly display = (s: SuggestionResponse | string | null): string =>
    typeof s === 'string' ? s : (s?.title ?? '');
}
