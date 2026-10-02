import { CourseSummary, CursorPage } from '../app/core/api/catalog-api';

/** Shared catalog test data — a plain module, so specs can import it without re-running a spec. */
export const K8S: CourseSummary = {
  id: '1',
  slug: 'k8s',
  title: 'Kubernetes Basics',
  subtitle: 'Pods and services',
  level: 'BEGINNER',
  language: 'en',
  status: 'PUBLISHED',
  priceMinor: 149900,
  currency: 'INR',
  ratingAverage: 4.6,
  ratingCount: 120,
  lectureCount: 12,
  totalDurationSeconds: 5040,
  instructorName: 'Asha Rao',
  category: { slug: 'containers-kubernetes', name: 'Containers & Kubernetes' },
  publishedAt: '2026-09-01T10:00:00Z',
};

/** Course n, a copy of K8S with its own id and slug. */
export function course(n: number): CourseSummary {
  return { ...K8S, id: String(n), slug: `course-${n}`, title: `Course ${n}` };
}

/** A page of courses `from`..`to` (inclusive), like the API sends it. */
export function page(
  from: number,
  to: number,
  nextCursor: string | null,
): CursorPage<CourseSummary> {
  const items: CourseSummary[] = [];
  for (let n = from; n <= to; n++) {
    items.push(course(n));
  }
  return { items, nextCursor };
}
