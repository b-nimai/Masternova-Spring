import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { SearchApi, SearchMode } from '../../../core/api/search-api';
import { CourseCard } from '../../catalog/course-card/course-card';

const MODES: readonly SearchMode[] = ['HYBRID', 'KEYWORD', 'SEMANTIC'];

/**
 * /search?q=…&mode=… — ranked course search (Phase 11.5).
 *
 * ⭐ The URL is the state: `q` and `mode` are query params bound to inputs, so a result page can
 *    be shared, reloaded and walked back to. The resource re-runs (cancelling the previous request)
 *    whenever either changes.
 * ⭐ `degraded` is not an error: the server answered from keywords because the embedding model
 *    was unavailable. The page says so in one line and shows the results.
 */
@Component({
  selector: 'app-search-results',
  imports: [MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule, CourseCard],
  templateUrl: './search-results.html',
  styleUrl: './search-results.scss',
})
export class SearchResults {
  private readonly api = inject(SearchApi);
  private readonly router = inject(Router);

  readonly q = input<string | undefined>();
  readonly mode = input<string | undefined>();

  protected readonly modes = MODES;
  protected readonly modeLabels: Record<SearchMode, string> = {
    HYBRID: 'Best match',
    KEYWORD: 'Keywords',
    SEMANTIC: 'Meaning',
  };

  /** An unknown `?mode=` (anyone can edit a url) falls back to the default. */
  protected readonly selectedMode = computed<SearchMode>(() => {
    const mode = this.mode();
    return MODES.includes(mode as SearchMode) ? (mode as SearchMode) : 'HYBRID';
  });

  protected readonly query = computed(() => this.q()?.trim() ?? '');

  protected readonly results = rxResource({
    params: () => (this.query() ? { q: this.query(), mode: this.selectedMode() } : undefined),
    stream: ({ params }) => this.api.search(params.q, params.mode),
  });

  protected changeMode(mode: SearchMode): void {
    void this.router.navigate([], {
      queryParams: { mode: mode === 'HYBRID' ? null : mode },
      queryParamsHandling: 'merge',
    });
  }
}
