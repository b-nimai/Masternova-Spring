import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LearningApi, LearningCourseResponse } from '../../../core/api/learning-api';

/** The dashboard's tabs (the Figma's "All / Pending / Completed"). */
export type LearningFilter = 'ALL' | 'IN_PROGRESS' | 'COMPLETED';

/** What a row's button says — one rule, used by the template and its tests. */
export type NextStep = 'START' | 'CONTINUE' | 'REVIEW';

export function nextStep(course: LearningCourseResponse): NextStep {
  if (course.continueLectureId === null) return 'REVIEW'; // everything watched
  return course.lastActivityAt === null ? 'START' : 'CONTINUE';
}

const isCompleted = (c: LearningCourseResponse) =>
  c.totalLectures > 0 && c.continueLectureId === null;

/**
 * /learning — "My Learning": the learner's courses, most recently watched first, each with how far
 * they got and a button that opens the right lecture (the server picks it: `continueLectureId`).
 *
 * ⭐ The percentages are rollups, up to 30 s behind the player (ADR-0022): fine for a dashboard.
 *    The course page and the player read through the buffer instead.
 * ⭐ Filtering is client-side over one `rxResource`: a learner's own courses are a short list,
 *    and the API isn't paged (API conventions §15).
 */
@Component({
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  selector: 'app-my-learning',
  styleUrl: './my-learning.scss',
  templateUrl: './my-learning.html',
})
export class MyLearning {
  private readonly api = inject(LearningApi);

  protected readonly courses = rxResource({ stream: () => this.api.dashboard() });
  protected readonly filter = signal<LearningFilter>('ALL');

  protected readonly shown = computed(() => {
    const all = this.courses.hasValue() ? this.courses.value() : [];
    switch (this.filter()) {
      case 'ALL':
        return all;
      case 'IN_PROGRESS':
        return all.filter((c) => !isCompleted(c));
      case 'COMPLETED':
        return all.filter(isCompleted);
    }
  });

  protected readonly nextStep = nextStep;
  protected readonly labels: Record<NextStep, string> = {
    START: 'Start',
    CONTINUE: 'Continue',
    REVIEW: 'Review course',
  };
}
