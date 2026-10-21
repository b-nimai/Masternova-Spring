import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { CourseSummary } from './catalog-api';

/** Mirrors `SearchMode`. */
export type SearchMode = 'KEYWORD' | 'SEMANTIC' | 'HYBRID';

/**
 * Mirrors `SearchResponse`: ranked, best first, no cursor. `mode` is what actually ran;
 * `degraded` = semantic ranking was asked for but unavailable, so these are keyword matches.
 */
export interface SearchResponse {
  mode: SearchMode;
  degraded: boolean;
  items: CourseSummary[];
}

/** Mirrors `SuggestionResponse`: one typeahead row. */
export interface SuggestionResponse {
  slug: string;
  title: string;
}

/** The typeahead asks from this many characters (the server refuses fewer). */
export const MIN_SUGGEST_LENGTH = 2;

/** Course search (API conventions §17). Public: no sign-in needed. */
@Service()
export class SearchApi {
  private readonly http = inject(HttpClient);

  search(q: string, mode: SearchMode = 'HYBRID', limit = 20): Observable<SearchResponse> {
    const params = new HttpParams().set('q', q).set('mode', mode).set('limit', limit);
    return this.http.get<SearchResponse>('/api/v1/search/courses', { params });
  }

  suggest(q: string): Observable<SuggestionResponse[]> {
    return this.http.get<SuggestionResponse[]>('/api/v1/search/suggestions', {
      params: new HttpParams().set('q', q),
    });
  }
}
