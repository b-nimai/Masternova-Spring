import { Component, input } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { SectionResponse } from '../../../core/api/catalog-api';
import { DurationPipe } from '../../../shared/duration-pipe';

/**
 * The course's sections and lectures. Rendered inside an `@defer (on viewport)` block on the course
 * page, so this component — and Material's expansion panel it pulls in — is a SEPARATE chunk,
 * downloaded only when the visitor scrolls down to it.
 */
@Component({
  imports: [MatExpansionModule, MatIconModule, DurationPipe],
  selector: 'app-curriculum',
  styleUrl: './curriculum.scss',
  templateUrl: './curriculum.html',
})
export class Curriculum {
  readonly sections = input.required<SectionResponse[]>();

  protected sectionSeconds(section: SectionResponse): number {
    return section.lectures.reduce((sum, l) => sum + l.durationSeconds, 0);
  }
}
