import { Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { AuthStore } from '../../core/auth/auth-store';
import { CodePanel, CodeToken } from './code-panel/code-panel';
import { CourseHighlights } from './course-highlights/course-highlights';
import { LearnerReviews } from './learner-reviews/learner-reviews';
import { SkillsSection } from './skills-section/skills-section';

/**
 * The landing page (Figma "Log In Page" 11167:17977, which is the home page): marketing sections
 * around real data: the three best-rated courses (CourseHighlights), the latest reviews of the best
 * of them (LearnerReviews), the number of subjects.
 */
@Component({
  selector: 'app-home',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    CodePanel,
    CourseHighlights,
    SkillsSection,
    LearnerReviews,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly catalog = inject(CatalogApi);
  protected readonly auth = inject(AuthStore);

  protected readonly topCourses = rxResource({
    stream: () => this.catalog.browse({ sort: 'HIGHEST_RATED' }, null, 3).pipe(map((p) => p.items)),
  });

  protected readonly subjects = toSignal(
    this.catalog.categories().pipe(map((categories) => categories.length)),
    { initialValue: 0 },
  );

  /** "Become an instructor": sign up first; an instructor goes to their courses. */
  protected readonly teachLink = computed(() =>
    this.auth.hasAnyRole(['INSTRUCTOR', 'ADMIN'])
      ? '/instructor'
      : this.auth.isAuthenticated()
        ? null // a learner asks an admin for the role: there is no self-service page
        : '/signup',
  );

  protected readonly firstPanel: readonly CodeToken[][] = [
    [{ text: '@Component({', kind: 'keyword' }],
    [{ text: '  selector: ' }, { text: "'app-lecture'", kind: 'string' }, { text: ',' }],
    [{ text: '  template: ' }, { text: '`<app-hls-player [src]="ticket()" />`', kind: 'string' }],
    [{ text: '})' }],
    [{ text: 'export class ', kind: 'keyword' }, { text: 'Lecture {' }],
    [{ text: '  readonly ' }, { text: 'ticket', kind: 'attr' }, { text: ' = rxResource({' }],
    [{ text: '    params: () => this.lectureId(),' }],
    [{ text: '    stream: ({ params }) =>' }],
    [{ text: '      this.playback.ticket(params),', kind: 'tag' }],
    [{ text: '  });' }],
    [{ text: '}' }],
  ];

  protected readonly secondPanel: readonly CodeToken[][] = [
    [{ text: '@Transactional', kind: 'keyword' }],
    [{ text: 'public ' }, { text: 'Access', kind: 'tag' }, { text: ' canWatch(' }],
    [
      { text: '    UserId', kind: 'tag' },
      { text: ' who, ' },
      { text: 'LectureId', kind: 'tag' },
      { text: ' what) {' },
    ],
    [{ text: '  var lecture = catalog.lecture(what);' }],
    [
      { text: '  if', kind: 'keyword' },
      { text: ' (lecture.' },
      { text: 'isPreview', kind: 'attr' },
      { text: '()) {' },
    ],
    [{ text: '    return ' }, { text: 'Access.granted()', kind: 'string' }, { text: ';' }],
    [{ text: '  }' }],
    [{ text: '  return ' }, { text: 'entitlements', kind: 'attr' }],
    [{ text: '      .find(who, lecture.courseId())' }],
    [{ text: '      .map(Entitlement::access)' }],
    [
      { text: '      .orElse(' },
      { text: 'Access.denied(NOT_ENROLLED)', kind: 'string' },
      { text: ');' },
    ],
  ];
}
