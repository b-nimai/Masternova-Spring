import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { CursorPage } from '../../core/api/cursor-page';
import { Role, User } from '../../core/auth/auth-api';

/** Mirrors `PendingApplicationResponse`: one row of the instructor-application queue. */
export interface PendingApplication {
  userId: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  pitch: string | null;
  appliedAt: string;
}

/**
 * /api/v1/admin/users and /api/v1/admin/instructor-applications — ADMIN only. Feature-scoped (not core/): only the admin page needs it, so it
 * ships in the admin chunk. The SERVER enforces ADMIN (@PreAuthorize); the route guard is UX only.
 */
@Service()
export class AdminApi {
  private readonly http = inject(HttpClient);

  getUser(id: string): Observable<User> {
    return this.http.get<User>(`/api/v1/admin/users/${encodeURIComponent(id)}`);
  }

  /** Admins know people by email (the server normalises it). */
  findByEmail(email: string): Observable<User> {
    return this.http.get<User>('/api/v1/admin/users', { params: { email } });
  }

  /** Pending, oldest first. */
  pendingApplications(): Observable<CursorPage<PendingApplication>> {
    return this.http.get<CursorPage<PendingApplication>>('/api/v1/admin/instructor-applications');
  }

  /** Idempotent; approve also grants INSTRUCTOR. 409 APPLICATION_DECIDED the other way. */
  decide(userId: string, decision: 'approve' | 'reject'): Observable<unknown> {
    return this.http.post(
      `/api/v1/admin/instructor-applications/${encodeURIComponent(userId)}/${decision}`,
      null,
    );
  }

  /** Replaces the user's roles (PUT = the full set, not a delta). Mirrors `ChangeRolesRequest`. */
  changeRoles(id: string, roles: Role[]): Observable<User> {
    return this.http.put<User>(`/api/v1/admin/users/${encodeURIComponent(id)}/roles`, { roles });
  }
}
