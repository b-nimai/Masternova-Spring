import { UrlTree } from '@angular/router';

/**
 * What counts as "a new page": the primary outlet's path, without query or fragment. ONE rule for
 * the two places that ask (scroll-to-top.ts, page-transitions.ts), so a catalog filter neither
 * jumps to the top nor fades the page.
 */
export function pagePath(tree: UrlTree): string {
  return tree.root.children['primary']?.segments.map((s) => s.path).join('/') ?? '';
}
