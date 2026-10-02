import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  Observable,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { CourseSummary } from './course-catalog';

/** Everything the search box can show — a discriminated union, like a sealed type in Java. */
export type SearchState =
  | { kind: 'idle' }
  | { kind: 'loading'; term: string }
  | { kind: 'ok'; term: string; items: CourseSummary[] }
  | { kind: 'error'; term: string; message: string };

/**
 * Turns a stream of keystrokes into a stream of search states — the classic "typeahead".
 *
 * A PURE function of its inputs (no Angular inside), so it is tested with marble diagrams in
 * course-search.spec.ts. Read the operators top to bottom:
 */
export function searchStates(
  query$: Observable<string>,
  search: (term: string) => Observable<CourseSummary[]>,
  debounceMs = 300,
): Observable<SearchState> {
  return query$.pipe(
    map((query) => query.trim()),
    debounceTime(debounceMs), //        ⭐ wait until typing PAUSES — not one request per keystroke
    distinctUntilChanged(), //          ⭐ "java" → "java " → "java" is still one search
    switchMap((term) =>
      // ⭐ switchMap: a new term CANCELS the previous in-flight search — results for an old term
      //    can never overwrite newer ones (a race you'd get with mergeMap)
      term.length < 2
        ? of<SearchState>({ kind: 'idle' })
        : search(term).pipe(
            map((items): SearchState => ({ kind: 'ok', term, items })),
            // ⭐ catchError INSIDE switchMap: one failed search becomes an error state and the
            //    outer stream lives on. Outside, the first error would end the search box forever.
            catchError((error: Error) =>
              of<SearchState>({ kind: 'error', term, message: error.message }),
            ),
            startWith<SearchState>({ kind: 'loading', term }),
          ),
    ),
  );
}
