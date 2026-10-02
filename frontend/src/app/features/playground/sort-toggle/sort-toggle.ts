import { Component, model } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export type SortDirection = 'asc' | 'desc';

/**
 * ⭐ model(): a TWO-WAY binding. The parent writes [(direction)]="sort" and both sides stay in
 * sync — the child can update it, the parent's signal changes. (React: a controlled component
 * with value + onChange in one.)
 */
@Component({
  selector: 'app-sort-toggle',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './sort-toggle.html',
  styleUrl: './sort-toggle.scss',
})
export class SortToggle {
  readonly direction = model<SortDirection>('asc');

  toggle(): void {
    this.direction.update((d) => (d === 'asc' ? 'desc' : 'asc'));
  }
}
