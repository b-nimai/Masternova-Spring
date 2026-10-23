import { ActivatedRouteSnapshot, ViewTransitionInfo } from '@angular/router';
import { prefersReducedMotion } from '../../shared/motion/reduced-motion';

/**
 * The router's View Transitions (app.config.ts) cross-fade the page on every navigation. Keep that
 * for a NEW page only, by the same rule as scroll-to-top.ts: the catalog's filters and sort tabs
 * are query-param navigations, and fading the whole page on each chip click would be noise. Also
 * skipped under "reduce motion". The animation itself is CSS (styles/_motion.scss).
 */
export function skipUnlessPageChanges({ transition, from, to }: ViewTransitionInfo): void {
  if (prefersReducedMotion() || pathOf(from) === pathOf(to)) {
    transition.skipTransition();
  }
}

/** `courses/k8s` for the deepest route of a snapshot tree (query and fragment left out). */
function pathOf(snapshot: ActivatedRouteSnapshot): string {
  let leaf = snapshot;
  while (leaf.firstChild) {
    leaf = leaf.firstChild;
  }
  return leaf.pathFromRoot.flatMap((route) => route.url.map((segment) => segment.path)).join('/');
}
