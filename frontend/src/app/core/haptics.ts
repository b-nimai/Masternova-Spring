import { Service } from '@angular/core';
import { prefersReducedMotion } from '../shared/motion/reduced-motion';

/**
 * A short vibration that confirms an action: added to the cart, enrolled, saved; or a buzz when
 * it failed. The Vibration API: Android browsers vibrate; iPhones (Safari has no such API) and
 * desktops do nothing, which is the right fallback for a nicety. Off under "reduce motion".
 *
 * Call it from the handler of the user's own click: browsers ignore vibrate() without a recent
 * user gesture.
 */
@Service()
export class Haptics {
  /** A light tick: a toggle, a removal. */
  tap(): void {
    this.play(10);
  }

  /** Two quick pulses: something was saved or bought. */
  success(): void {
    this.play([14, 70, 22]);
  }

  /** A longer double buzz: it didn't work. */
  error(): void {
    this.play([40, 60, 40]);
  }

  private play(pattern: number | number[]): void {
    if (typeof navigator === 'undefined' || !('vibrate' in navigator) || prefersReducedMotion()) {
      return;
    }
    try {
      navigator.vibrate(pattern);
    } catch {
      // a blocked vibration (no user gesture, a permissions policy) is not an error worth surfacing
    }
  }
}
