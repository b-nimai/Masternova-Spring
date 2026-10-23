/**
 * The OS "reduce motion" setting. Every effect in the app (page transitions, scroll reveals, the
 * pointer glow, haptics) asks this first and does nothing when it says yes (WCAG 2.3.3).
 */
export function prefersReducedMotion(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** A mouse or trackpad that hovers: where a pointer-following effect makes sense. */
export function hasFinePointer(): boolean {
  return (
    typeof matchMedia === 'function' && matchMedia('(hover: hover) and (pointer: fine)').matches
  );
}
