import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors the backend enums (catalog/domain). */
export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ALL_LEVELS';
export type CourseStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
export type CourseSort = 'NEWEST' | 'HIGHEST_RATED' | 'PRICE_LOW' | 'PRICE_HIGH';
export type PriceFilter = 'FREE' | 'PAID';
export type LectureKind = 'VIDEO' | 'ARTICLE';

export const COURSE_LEVELS: readonly CourseLevel[] = [
  'BEGINNER',
  'INTERMEDIATE',
  'ADVANCED',
  'ALL_LEVELS',
];
export const COURSE_SORTS: readonly CourseSort[] = [
  'NEWEST',
  'HIGHEST_RATED',
  'PRICE_LOW',
  'PRICE_HIGH',
];

/** Mirrors `CursorPage<T>` — API conventions §2: no total, an opaque cursor or null. */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

/** Mirrors `CategoryRef`. */
export interface CategoryRef {
  slug: string;
  name: string;
}

/** Mirrors `CategoryResponse` (two levels). */
export interface CategoryResponse {
  slug: string;
  name: string;
  children: CategoryResponse[];
}

/** Mirrors `CourseSummary`. Money is minor units + currency (API conventions §5). */
export interface CourseSummary {
  id: string;
  slug: string;
  title: string;
  subtitle: string | null;
  level: CourseLevel;
  language: string;
  status: CourseStatus;
  priceMinor: number;
  currency: string;
  ratingAverage: number;
  ratingCount: number;
  lectureCount: number;
  totalDurationSeconds: number;
  instructorName: string;
  category: CategoryRef;
  publishedAt: string | null;
}

/** Mirrors `CourseDetailResponse.LectureResponse`. */
export interface LectureResponse {
  id: string;
  title: string;
  kind: LectureKind;
  preview: boolean;
  durationSeconds: number;
}

/** Mirrors `CourseDetailResponse.SectionResponse`. */
export interface SectionResponse {
  id: string;
  title: string;
  lectures: LectureResponse[];
}

/** Mirrors `CourseDetailResponse`. */
export interface CourseDetailResponse extends Omit<CourseSummary, 'subtitle'> {
  subtitle: string | null;
  description: string;
  enrollmentCount: number;
  /** Pricing was confirmed (free counts): the publish gate's PRICE_NOT_SET. */
  priceSet: boolean;
  /** ⭐ The optimistic-concurrency token: editors send it back as `expectedVersion`. */
  version: number;
  sections: SectionResponse[];
}

/** The facets a visitor chose — every one optional. The URL query string holds exactly these. */
export interface CourseQuery {
  q?: string;
  category?: string;
  level?: CourseLevel[];
  price?: PriceFilter;
  sort?: CourseSort;
}

/** The public catalog (docs/lld/catalog.md §5). */
@Service()
export class CatalogApi {
  private readonly http = inject(HttpClient);

  /**
   * One keyset page. `cursor` is the previous page's `nextCursor` (null for the first page) — opaque:
   * never parse it, never build one.
   */
  browse(
    query: CourseQuery,
    cursor: string | null,
    limit = 20,
  ): Observable<CursorPage<CourseSummary>> {
    // ⭐ fromObject turns an array into REPEATED params (?level=A&level=B), which the backend binds
    //    to a Set; absent facets are left out instead of being sent as "undefined"
    const params = new HttpParams({
      fromObject: {
        limit,
        ...(query.q ? { q: query.q } : {}),
        ...(query.category ? { category: query.category } : {}),
        ...(query.level?.length ? { level: query.level } : {}),
        ...(query.price ? { price: query.price } : {}),
        ...(query.sort ? { sort: query.sort } : {}),
        ...(cursor ? { cursor } : {}),
      },
    });
    return this.http.get<CursorPage<CourseSummary>>('/api/v1/courses', { params });
  }

  /** The course page. 404 NOT_FOUND for a missing course and for a draft you may not see. */
  course(slug: string): Observable<CourseDetailResponse> {
    return this.http.get<CourseDetailResponse>(`/api/v1/courses/${encodeURIComponent(slug)}`);
  }

  categories(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>('/api/v1/categories');
  }
}
