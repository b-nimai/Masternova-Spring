import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors `EnrollmentResponse`: the answer to a free enrollment. */
export interface EnrollmentResponse {
  courseId: string;
  status: 'ACTIVE' | 'REVOKED';
  enrolledAt: string;
}

/** Mirrors `LearningCourseResponse`: a card on "My Learning" (rollups, ≤ 30 s behind). */
export interface LearningCourseResponse {
  courseId: string;
  slug: string;
  title: string;
  instructorName: string;
  completedLectures: number;
  totalLectures: number;
  percentComplete: number;
  enrolledAt: string;
  /** null = enrolled, never watched. */
  lastActivityAt: string | null;
  /** The lecture "Continue" opens; null = everything watched, or nothing to watch. */
  continueLectureId: string | null;
}

/** Mirrors `CourseProgressResponse`: one course for its learner, read through the buffer. */
export interface CourseProgressResponse {
  courseId: string;
  enrolled: boolean;
  percentComplete: number;
  completedLectureIds: string[];
  continueLectureId: string | null;
  continuePositionSeconds: number;
}

/** Mirrors `LectureProgressResponse`: where the player starts. */
export interface LectureProgressResponse {
  lectureId: string;
  positionSeconds: number;
  maxPositionSeconds: number;
  completed: boolean;
}

/** Mirrors `HeartbeatResponse`: the credential for this page's last beacon. */
export interface HeartbeatResponse {
  beaconToken: string;
  beaconExpiresAt: string;
}

/** The enrollment module's API (API conventions §15): my courses and where I stopped. */
@Service()
export class LearningApi {
  private readonly http = inject(HttpClient);

  /** Idempotent PUT: enrolling twice is one enrollment. Free, published courses only. */
  enroll(courseId: string): Observable<EnrollmentResponse> {
    return this.http.put<EnrollmentResponse>(`/api/v1/me/enrollments/${courseId}`, null);
  }

  dashboard(): Observable<LearningCourseResponse[]> {
    return this.http.get<LearningCourseResponse[]>('/api/v1/me/learning');
  }

  courseProgress(courseId: string): Observable<CourseProgressResponse> {
    return this.http.get<CourseProgressResponse>(`/api/v1/me/courses/${courseId}/progress`);
  }

  lectureProgress(lectureId: string): Observable<LectureProgressResponse> {
    return this.http.get<LectureProgressResponse>(`/api/v1/me/progress/${lectureId}`);
  }

  /** Every 15 s while playing, and on pause and seek. */
  heartbeat(lectureId: string, positionSeconds: number): Observable<HeartbeatResponse> {
    return this.http.put<HeartbeatResponse>(`/api/v1/me/progress/${lectureId}`, {
      positionSeconds,
    });
  }

  /**
   * ⭐ The last word of a closing page. `navigator.sendBeacon` is the one request the browser
   *    promises to send while the page unloads — but it can't set headers, so it bypasses
   *    `HttpClient` (and the auth interceptor) and carries its credential in the body.
   *
   * @returns whether the browser queued it
   */
  beacon(token: string, positionSeconds: number): boolean {
    const body = new Blob([JSON.stringify({ token, positionSeconds })], {
      type: 'application/json',
    });
    return navigator.sendBeacon?.('/api/v1/progress/beacon', body) ?? false;
  }
}
