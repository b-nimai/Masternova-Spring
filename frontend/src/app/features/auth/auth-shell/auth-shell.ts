import { Component, input } from '@angular/core';

/**
 * The auth pages' frame (Figma "Log In Page" 11167:20157, "Sign up" 11167:20280): heading +
 * tagline + the projected form on the left, the photo on its cross-hatched backdrop on the right.
 * `photo = false` centres a single narrow column instead ("Verify email" 11167:20616).
 */
@Component({
  selector: 'app-auth-shell',
  templateUrl: './auth-shell.html',
  styleUrl: './auth-shell.scss',
})
export class AuthShell {
  readonly heading = input.required<string>();
  readonly tagline = input<string>();
  /** Edu SA Beginner, blue, underlined by the brush stroke ("Be Unstoppable"). */
  readonly accent = input<string>();
  readonly photo = input(true);

  /**
   * The backdrop is six copies of the 385 × 194 cross-hatch, each turned 90° into a 194 × 385
   * cell (3 columns × 2 rows, as in the Figma). A turned box keeps its centre, so each tile is
   * offset by half the difference of its sides.
   */
  protected readonly tiles = [0, 194, 388].flatMap((left) =>
    [5, 390].map((top) => `left: ${left - 95.5}px; top: ${top + 95.5}px`),
  );
}
