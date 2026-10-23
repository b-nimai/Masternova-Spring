import { Component, computed, effect, inject, input, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { CourseDetailResponse, SectionResponse } from '../../../core/api/catalog-api';
import { EntitlementApi } from '../../../core/api/entitlement-api';
import { LearningApi } from '../../../core/api/learning-api';
import { DurationPipe } from '../../../shared/duration-pipe';

/**
 * The player's left column (Figma "Video Detail" 11167:21922): the course's title and how many
 * lectures are done, then its sections, each lecture ticked when completed, the current one
 * highlighted, and every other video this viewer may open linked.
 *
 * ⭐ Both resources are keyed on the course only. Moving to another lecture RELOADS the progress
 * (a reload keeps the current value while it runs: no flicker to 0/N, no ticks lost), so the
 * lecture just left shows its tick once the server has it.
 */
@Component({
  selector: 'app-lecture-sidebar',
  imports: [RouterLink, MatButtonModule, MatExpansionModule, MatIconModule, DurationPipe],
  templateUrl: './lecture-sidebar.html',
  styleUrl: './lecture-sidebar.scss',
})
export class LectureSidebar {
  private readonly learning = inject(LearningApi);
  private readonly entitlements = inject(EntitlementApi);

  readonly course = input.required<CourseDetailResponse>();
  readonly currentLectureId = input.required<string>();

  /** Failing open: no progress (not enrolled, the instructor, an error) just means no ticks. */
  private readonly progress = rxResource({
    params: () => this.course().id,
    stream: ({ params: courseId }) =>
      this.learning.courseProgress(courseId).pipe(catchError(() => of(null))),
  });

  constructor() {
    let previous: string | undefined;
    effect(() => {
      const lecture = this.currentLectureId();
      if (previous !== undefined && previous !== lecture) {
        untracked(() => this.progress.reload());
      }
      previous = lecture;
    });
  }

  private readonly access = rxResource({
    params: () => this.course().id,
    stream: ({ params: courseId }) =>
      this.entitlements.courseAccess(courseId).pipe(catchError(() => of(null))),
  });

  protected readonly completed = computed<ReadonlySet<string>>(
    () => new Set(this.progress.value()?.completedLectureIds ?? []),
  );

  /** Lectures this viewer may open in the player: allowed videos. */
  protected readonly playable = computed<ReadonlySet<string>>(() => {
    const access = this.access.value();
    const allowed = new Set(
      access?.lectures.filter((l) => l.access.kind === 'ALLOWED').map((l) => l.lectureId) ?? [],
    );
    return new Set(
      this.course()
        .sections.flatMap((s) => s.lectures)
        .filter((l) => l.kind === 'VIDEO' && allowed.has(l.id))
        .map((l) => l.id),
    );
  });

  /** The Figma's "Add Review": enrolled learners may review (the form is on the course page). */
  protected readonly canReview = computed(() => this.progress.value()?.enrolled === true);

  protected readonly total = computed(() =>
    this.course().sections.reduce((sum, s) => sum + s.lectures.length, 0),
  );

  protected contains(section: SectionResponse, lectureId: string): boolean {
    return section.lectures.some((l) => l.id === lectureId);
  }

  protected sectionSeconds(section: SectionResponse): number {
    return section.lectures.reduce((sum, l) => sum + l.durationSeconds, 0);
  }
}
