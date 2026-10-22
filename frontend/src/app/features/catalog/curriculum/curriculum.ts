import { Component, computed, input, viewChild } from '@angular/core';
import { MatAccordion, MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { SectionResponse } from '../../../core/api/catalog-api';
import { AccessResponse } from '../../../core/api/entitlement-api';
import { DurationPipe } from '../../../shared/duration-pipe';

/**
 * The course's sections and lectures. Rendered inside an `@defer (on viewport)` block on the course
 * page, so this component — and Material's expansion panel it pulls in — is a SEPARATE chunk,
 * downloaded only when the visitor scrolls down to it.
 *
 * Phase 8: each lecture shows the entitlement engine's answer — an open video links to the player,
 * a locked one shows a lock. Until the answer arrives (`access` null), lectures are just listed.
 * Phase 10: a lecture the learner has completed shows a tick.
 * Phase UI: the Figma's summary line and "Collapse all sections".
 */
@Component({
  imports: [MatExpansionModule, MatIconModule, DurationPipe, RouterLink],
  selector: 'app-curriculum',
  styleUrl: './curriculum.scss',
  templateUrl: './curriculum.html',
})
export class Curriculum {
  readonly sections = input.required<SectionResponse[]>();
  readonly slug = input.required<string>();
  /** lectureId → may this viewer watch it; null while it loads (or if it failed). */
  readonly access = input<ReadonlyMap<string, AccessResponse> | null>(null);
  /** Phase 10: the lectures this learner has completed (a tick instead of the play icon). */
  readonly completed = input<ReadonlySet<string>>(new Set());
  /** The course's total length, for the summary line. */
  readonly totalSeconds = input(0);

  protected readonly lectureCount = computed(() =>
    this.sections().reduce((sum, s) => sum + s.lectures.length, 0),
  );
  private readonly accordion = viewChild(MatAccordion);

  protected collapseAll(): void {
    this.accordion()?.closeAll();
  }

  protected sectionSeconds(section: SectionResponse): number {
    return section.lectures.reduce((sum, l) => sum + l.durationSeconds, 0);
  }
}
