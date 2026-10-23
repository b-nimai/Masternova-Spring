import { DestroyRef, Directive, ElementRef, inject, input } from '@angular/core';
import { prefersReducedMotion } from './reduced-motion';
import { ScrollReveals } from './scroll-reveals';

/**
 * Fades and lifts an element in the first time it scrolls into view (styles/_motion.scss).
 *
 * ⭐ The element is hidden only by THIS directive, only once it is watching: no JavaScript, no
 * IntersectionObserver, or "reduce motion" → it was never hidden. Content can't get stuck
 * invisible.
 *
 *   <section appReveal>…</section>
 *   <li appReveal [appRevealDelay]="$index * 80">…</li>   (a stagger)
 */
@Directive({
  selector: '[appReveal]',
  host: { '[style.--mn-reveal-delay.ms]': 'appRevealDelay()' },
})
export class Reveal {
  readonly appRevealDelay = input(0);

  constructor() {
    const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
      return;
    }
    const reveals = inject(ScrollReveals);
    element.classList.add('mn-reveal');
    reveals.watch(element);
    inject(DestroyRef).onDestroy(() => reveals.forget(element));
  }
}
