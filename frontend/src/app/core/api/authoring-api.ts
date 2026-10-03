import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CourseDetailResponse,
  CourseLevel,
  CourseSummary,
  CursorPage,
  LectureKind,
  SectionResponse,
} from './catalog-api';

/** Mirrors `CreateCourseRequest`. */
export interface CreateCourseRequest {
  title: string;
  categorySlug: string;
  level: CourseLevel;
  language: string;
}

/** Mirrors `UpdateDetailsRequest` minus the version (added by the client). */
export interface CourseDetails {
  title: string;
  subtitle: string | null;
  description: string;
  categorySlug: string;
  level: CourseLevel;
  language: string;
}

/** Mirrors `ReadinessResponse`. */
export interface ReadinessResponse {
  ready: boolean;
  requirements: { code: string; message: string; satisfied: boolean }[];
}

/** Mirrors `CurriculumResponse`. */
export interface CurriculumResponse {
  version: number;
  canUndo: boolean;
  canRedo: boolean;
  lectureCount: number;
  totalDurationSeconds: number;
  sections: SectionResponse[];
}

/**
 * Mirrors the sealed `CurriculumCommand` (API conventions §6): a discriminated union on `kind`.
 * ⭐ TypeScript narrows on `kind` exactly like a Java `switch` over the sealed records.
 */
export type CurriculumCommand =
  | { kind: 'ADD_SECTION'; title: string }
  | { kind: 'RENAME_SECTION'; sectionId: string; title: string }
  | { kind: 'REORDER_SECTIONS'; order: string[] }
  | { kind: 'REMOVE_SECTION'; sectionId: string }
  | {
      kind: 'ADD_LECTURE';
      sectionId: string;
      title: string;
      lectureKind?: LectureKind;
      preview?: boolean;
      durationSeconds?: number;
    }
  | { kind: 'UPDATE_LECTURE'; lectureId: string; title: string; preview: boolean }
  | { kind: 'MOVE_LECTURE'; lectureId: string; toSectionId: string; toPosition: number }
  | { kind: 'REMOVE_LECTURE'; lectureId: string };

/** The author's lifecycle actions (publishing is the reviewer's). */
export type AuthorAction = 'submit' | 'withdraw' | 'unpublish' | 'archive';

/**
 * The instructor's side of the catalog (docs/lld/catalog-authoring.md §5). Every content write
 * carries `expectedVersion`: a stale tab gets 409 VERSION_CONFLICT instead of overwriting.
 */
@Service()
export class AuthoringApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/instructor/courses';

  myCourses(cursor: string | null = null): Observable<CursorPage<CourseSummary>> {
    const params = cursor ? new HttpParams().set('cursor', cursor) : undefined;
    return this.http.get<CursorPage<CourseSummary>>(this.base, { params });
  }

  /** ⭐ Unversioned (nothing exists yet), so it needs an Idempotency-Key: a retry can't duplicate. */
  create(request: CreateCourseRequest, idempotencyKey: string): Observable<CourseDetailResponse> {
    return this.http.post<CourseDetailResponse>(this.base, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  get(id: string): Observable<CourseDetailResponse> {
    return this.http.get<CourseDetailResponse>(`${this.base}/${id}`);
  }

  updateDetails(
    id: string,
    expectedVersion: number,
    details: CourseDetails,
  ): Observable<CourseDetailResponse> {
    return this.http.put<CourseDetailResponse>(`${this.base}/${id}/details`, {
      expectedVersion,
      ...details,
    });
  }

  confirmPrice(
    id: string,
    expectedVersion: number,
    priceMinor: number,
  ): Observable<CourseDetailResponse> {
    return this.http.put<CourseDetailResponse>(`${this.base}/${id}/pricing`, {
      expectedVersion,
      priceMinor,
    });
  }

  readiness(id: string): Observable<ReadinessResponse> {
    return this.http.get<ReadinessResponse>(`${this.base}/${id}/readiness`);
  }

  transition(id: string, action: AuthorAction): Observable<CourseDetailResponse> {
    return this.http.post<CourseDetailResponse>(`${this.base}/${id}/${action}`, null);
  }

  curriculum(id: string): Observable<CurriculumResponse> {
    return this.http.get<CurriculumResponse>(`${this.base}/${id}/curriculum`);
  }

  apply(
    id: string,
    expectedVersion: number,
    command: CurriculumCommand,
  ): Observable<CurriculumResponse> {
    return this.http.post<CurriculumResponse>(`${this.base}/${id}/curriculum`, {
      expectedVersion,
      command,
    });
  }

  undo(id: string, expectedVersion: number): Observable<CurriculumResponse> {
    return this.http.post<CurriculumResponse>(`${this.base}/${id}/curriculum/undo`, {
      expectedVersion,
    });
  }

  redo(id: string, expectedVersion: number): Observable<CurriculumResponse> {
    return this.http.post<CurriculumResponse>(`${this.base}/${id}/curriculum/redo`, {
      expectedVersion,
    });
  }
}
