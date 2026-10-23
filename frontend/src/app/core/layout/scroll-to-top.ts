import { ViewportScroller } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { pagePath } from './page-path';

/**
 * Start a new PAGE at the top, but not every navigation. The router's own
 * `scrollPositionRestoration: 'enabled'` scrolls on all of them, and the catalog's filters are
 * query-param navigations: every chip clicked would jump the page. So: scroll only when the
 * path changes, and leave a `#fragment` to the page that owns the anchor (the course page's
 * `#reviews` exists only once its data has loaded).
 *
 * Call in an injection context (the root component's constructor).
 */
export function scrollToTopOnPageChange(): void {
  const router = inject(Router);
  const scroller = inject(ViewportScroller);
  let lastPath: string | null = null;

  router.events
    .pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      takeUntilDestroyed(inject(DestroyRef)),
    )
    .subscribe((event) => {
      const tree = router.parseUrl(event.urlAfterRedirects);
      const path = pagePath(tree);
      if (lastPath !== null && path !== lastPath && !tree.fragment) {
        // instant: `scroll-behavior: smooth` (styles/_motion.scss) is for anchors, not a new page
        scroller.scrollToPosition([0, 0], { behavior: 'instant' });
      }
      lastPath = path;
    });
}
