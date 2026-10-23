import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors `InstructorApplication.Status`. */
export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Mirrors `InstructorApplicationResponse`. */
export interface InstructorApplicationResponse {
  status: ApplicationStatus;
  pitch: string | null;
  appliedAt: string;
  decidedAt: string | null;
}

/**
 * /api/v1/me/instructor-application — the signed-in user's request to teach. Feature-scoped: only
 * the account page asks (signup files one through AuthApi's `applyAsInstructor`).
 */
@Service()
export class InstructorApplicationApi {
  private readonly http = inject(HttpClient);

  /** 404 while the user never applied. */
  mine(): Observable<InstructorApplicationResponse> {
    return this.http.get<InstructorApplicationResponse>('/api/v1/me/instructor-application');
  }

  /** Mirrors `ApplyAsInstructorRequest`. 409 APPLICATION_PENDING, 422 ALREADY_INSTRUCTOR. */
  apply(pitch: string | null): Observable<InstructorApplicationResponse> {
    return this.http.post<InstructorApplicationResponse>('/api/v1/me/instructor-application', {
      pitch,
    });
  }
}
