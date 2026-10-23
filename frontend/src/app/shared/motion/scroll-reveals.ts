import { Service } from '@angular/core';

/**
 * ONE IntersectionObserver for every `appReveal` element on the page, not one each: the browser
 * batches the checks, and an element is let go the moment it has been revealed (it never hides
 * again, so there is nothing left to watch).
 */
@Service()
export class ScrollReveals {
  private observer?: IntersectionObserver;

  /** Adds `mn-revealed` (styles/_motion.scss) the first time any part of it is on screen. */
  watch(element: Element): void {
    this.observer ??= new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('mn-revealed');
            this.forget(entry.target);
          }
        }
      },
      // any pixel, a little above the bottom edge (content moves in as it arrives). Not a ratio:
      // a section taller than ten screens never shows 10 % of itself and would stay hidden
      { threshold: 0, rootMargin: '0px 0px -5% 0px' },
    );
    this.observer.observe(element);
  }

  forget(element: Element): void {
    this.observer?.unobserve(element);
  }
}
