import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthoringApi } from '../../../core/api/authoring-api';
import { CourseStatus } from '../../../core/api/catalog-api';
import { coverFor } from '../../../shared/course-cover';
import { DurationPipe } from '../../../shared/duration-pipe';
import { MoneyPipe } from '../../../shared/money-pipe';

/** /instructor — the instructor's own courses, every status, most recently edited first. */
@Component({
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    DurationPipe,
  ],
  selector: 'app-instructor-courses',
  styleUrl: './instructor-courses.scss',
  templateUrl: './instructor-courses.html',
})
export class InstructorCourses {
  private readonly api = inject(AuthoringApi);
  protected readonly courses = rxResource({ stream: () => this.api.myCourses() });
  protected readonly cover = coverFor;

  /** The Figma's status chips ("Published", "Drafted"), for every state a course can be in. */
  protected readonly statusLabel: Record<CourseStatus, string> = {
    DRAFT: 'Drafted',
    IN_REVIEW: 'In review',
    PUBLISHED: 'Published',
    ARCHIVED: 'Archived',
  };
  protected readonly statusIcon: Record<CourseStatus, string> = {
    DRAFT: 'schedule',
    IN_REVIEW: 'hourglass_top',
    PUBLISHED: 'check_circle',
    ARCHIVED: 'inventory_2',
  };
}
