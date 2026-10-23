import { ViewportScroller } from '@angular/common';
import { afterNextRender, DestroyRef, inject, Injector } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { pagePath } from './page-path';

/**
 * Start a new PAGE at the top, but not every navigation. The router's own
 * `scrollPositionRestoration: 'enabled'` scrolls on all of them, and the catalog's filters are
 * query-param navigations: every chip clicked would jump the page. So: scroll only when the
 * path changes. A `#fragment` scrolls to its anchor once the new page has rendered (the account
 * page's `#teach`); an anchor that needs data first scrolls itself (the course page's `#reviews`).
 *
 * Call in an injection context (the root component's constructor).
 */
export function scrollToTopOnPageChange(): void {
  const router = inject(Router);
  const scroller = inject(ViewportScroller);
  const injector = inject(Injector);
  let lastPath: string | null = null;

  router.events
    .pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      takeUntilDestroyed(inject(DestroyRef)),
    )
    .subscribe((event) => {
      const tree = router.parseUrl(event.urlAfterRedirects);
      const path = pagePath(tree);
      const fragment = tree.fragment;
      if (fragment) {
        afterNextRender(() => scroller.scrollToAnchor(fragment), { injector });
      } else if (lastPath !== null && path !== lastPath) {
        // instant: `scroll-behavior: smooth` (styles/_motion.scss) is for anchors, not a new page
        scroller.scrollToPosition([0, 0], { behavior: 'instant' });
      }
      lastPath = path;
    });
}
