import { CurriculumResponse } from '../app/core/api/authoring-api';
import { CourseDetailResponse } from '../app/core/api/catalog-api';
import { K8S } from './catalog-fixtures';

/** A course as the instructor endpoints return it. */
export function course(overrides: Partial<CourseDetailResponse> = {}): CourseDetailResponse {
  return {
    ...K8S,
    id: 'c1',
    status: 'DRAFT',
    description: 'A course about Kubernetes.',
    enrollmentCount: 0,
    priceSet: false,
    version: 0,
    sections: [],
    ...overrides,
  };
}

/** Intro [Welcome*, Setup] · Core [Pods] */
export function curriculum(overrides: Partial<CurriculumResponse> = {}): CurriculumResponse {
  return {
    version: 3,
    canUndo: true,
    canRedo: false,
    lectureCount: 3,
    totalDurationSeconds: 900,
    sections: [
      {
        id: 's1',
        title: 'Intro',
        lectures: [
          { id: 'l1', title: 'Welcome', kind: 'VIDEO', preview: true, durationSeconds: 90 },
          { id: 'l2', title: 'Setup', kind: 'VIDEO', preview: false, durationSeconds: 210 },
        ],
      },
      {
        id: 's2',
        title: 'Core',
        lectures: [
          { id: 'l3', title: 'Pods', kind: 'VIDEO', preview: false, durationSeconds: 600 },
        ],
      },
    ],
    ...overrides,
  };
}
