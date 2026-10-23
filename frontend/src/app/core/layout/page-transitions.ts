import {
  ActivatedRouteSnapshot,
  createUrlTreeFromSnapshot,
  ViewTransitionInfo,
} from '@angular/router';
import { prefersReducedMotion } from '../../shared/motion/reduced-motion';
import { pagePath } from './page-path';

/**
 * The router's View Transitions (app.config.ts) cross-fade the page on every navigation. Keep that
 * for a NEW page only, by the same rule as scroll-to-top.ts (`pagePath`): the catalog's filters and sort tabs
 * are query-param navigations, and fading the whole page on each chip click would be noise. Also
 * skipped under "reduce motion". The animation itself is CSS (styles/_motion.scss).
 */
export function skipUnlessPageChanges({ transition, from, to }: ViewTransitionInfo): void {
  if (prefersReducedMotion() || pathOf(from) === pathOf(to)) {
    transition.skipTransition();
  }
}

/** The snapshot's URL as a tree (no query: it isn't asked for), so `pagePath` can read it. */
function pathOf(root: ActivatedRouteSnapshot): string {
  return pagePath(createUrlTreeFromSnapshot(root, []));
}
