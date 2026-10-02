import { Service } from '@angular/core';
import { delay, Observable, of, switchMap, throwError, timer } from 'rxjs';

/** What the explorer shows for a course — mirrors a future backend `CourseSummary` DTO. */
export interface CourseSummary {
  id: string;
  title: string;
  category: string;
  priceMinor: number; // paise — money stays an integer on the wire too (API conventions §5)
  rating: number;
}

const COURSES: readonly CourseSummary[] = [
  {
    id: 'c1',
    title: 'Spring Boot Fundamentals',
    category: 'Backend',
    priceMinor: 149_900,
    rating: 4.7,
  },
  {
    id: 'c2',
    title: 'Java Streams Deep Dive',
    category: 'Backend',
    priceMinor: 99_900,
    rating: 4.9,
  },
  { id: 'c3', title: 'Angular Signals', category: 'Frontend', priceMinor: 129_900, rating: 4.5 },
  {
    id: 'c4',
    title: 'Kubernetes for Developers',
    category: 'DevOps',
    priceMinor: 199_900,
    rating: 4.8,
  },
  { id: 'c5', title: 'Docker in Practice', category: 'DevOps', priceMinor: 0, rating: 4.2 },
  { id: 'c6', title: 'RxJS Patterns', category: 'Frontend', priceMinor: 89_900, rating: 4.1 },
  {
    id: 'c7',
    title: 'System Design Basics',
    category: 'Backend',
    priceMinor: 249_900,
    rating: 4.6,
  },
];

/**
 * A fake search API (the real one arrives with the catalog module in Phase 5): answers after
 * 300 ms, and fails for the term "error" so the UI's error path can be tried.
 *
 * ⭐ Returns an Observable — like HttpClient does — so callers compose it with RxJS.
 */
@Service()
export class CourseCatalog {
  search(term: string): Observable<CourseSummary[]> {
    const needle = term.trim().toLowerCase();
    if (needle === 'error') {
      return timer(300).pipe(
        switchMap(() => throwError(() => new Error('Search is temporarily down'))),
      );
    }
    const matches = COURSES.filter(
      (c) => c.title.toLowerCase().includes(needle) || c.category.toLowerCase().includes(needle),
    );
    return of(matches).pipe(delay(300));
  }
}
