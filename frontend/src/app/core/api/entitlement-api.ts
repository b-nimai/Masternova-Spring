import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors `AccessGrant`: why access was allowed. */
export type AccessGrant = 'ADMIN' | 'OWNER' | 'FREE_COURSE' | 'PREVIEW_LECTURE' | 'ENTITLED';

/** Mirrors `DenialReason` (API conventions §10): why not — each one is a different next step. */
export type DenialReason =
  | 'NO_ENTITLEMENT'
  | 'ENTITLEMENT_EXPIRED'
  | 'ENTITLEMENT_REVOKED'
  | 'COURSE_NOT_PUBLISHED'
  | 'ACCESS_CHECK_FAILED';

/**
 * Mirrors `AccessResponse`: a DISCRIMINATED UNION on `kind`. ⭐ After `@switch (access.kind)`,
 * the `ALLOWED` branch can only read `grant` and the `DENIED` branch only `reason` — the compiler
 * checks the template, so a lock icon can't show a grant that isn't there.
 */
export type AccessResponse =
  { kind: 'ALLOWED'; grant: AccessGrant } | { kind: 'DENIED'; reason: DenialReason };

/** Mirrors `CourseAccessResponse`: the course-wide decision (the call to action) + one per lecture. */
export interface CourseAccessResponse {
  courseId: string;
  course: AccessResponse;
  lectures: { lectureId: string; access: AccessResponse }[];
}

/** Mirrors `LecturePlaybackResponse`: a 5-minute, IP-bound manifest url (ADR-0017). */
export interface LecturePlaybackResponse {
  manifestUrl: string;
  expiresAt: string;
}

/** The entitlement module's API: what may I watch, and a playback token when I may. */
@Service()
export class EntitlementApi {
  private readonly http = inject(HttpClient);

  /** Public: anonymous visitors get answers too (which lectures are previews). */
  courseAccess(courseId: string): Observable<CourseAccessResponse> {
    return this.http.get<CourseAccessResponse>(`/api/v1/courses/${courseId}/access`);
  }

  /** Public: the player route's guard asks before the page loads. */
  lectureAccess(lectureId: string): Observable<AccessResponse> {
    return this.http.get<AccessResponse>(`/api/v1/lectures/${lectureId}/access`);
  }

  /** ⭐ The ONE access check per ticket; the player then fetches the playlists itself. */
  playback(lectureId: string): Observable<LecturePlaybackResponse> {
    return this.http.post<LecturePlaybackResponse>(`/api/v1/lectures/${lectureId}/playback`, null);
  }
}
