import { DestroyRef, Directive, ElementRef, inject } from '@angular/core';
import { hasFinePointer, prefersReducedMotion } from './reduced-motion';

/**
 * A soft spotlight that follows the mouse across the element (the home hero, course cards). The
 * system cursor stays as it is: the glow decorates, it never replaces the pointer.
 *
 * ⭐ No change detection per mouse move: a plain listener writes two CSS variables, at most once
 * per frame, and the CSS (styles/_motion.scss) draws the gradient. Touch screens and "reduce
 * motion" get nothing.
 */
@Directive({ selector: '[appPointerGlow]' })
export class PointerGlow {
  constructor() {
    const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    if (prefersReducedMotion() || !hasFinePointer()) {
      return;
    }
    let frame = 0;
    let clientX = 0;
    let clientY = 0;
    const draw = () => {
      frame = 0;
      const box = element.getBoundingClientRect(); // read once per frame, not per event
      element.style.setProperty('--mn-pointer-x', `${clientX - box.left}px`);
      element.style.setProperty('--mn-pointer-y', `${clientY - box.top}px`);
    };
    const move = (event: PointerEvent) => {
      clientX = event.clientX;
      clientY = event.clientY;
      frame ||= requestAnimationFrame(draw);
    };
    element.classList.add('mn-pointer-glow');
    element.addEventListener('pointermove', move, { passive: true });
    inject(DestroyRef).onDestroy(() => {
      element.removeEventListener('pointermove', move);
      cancelAnimationFrame(frame);
    });
  }
}
