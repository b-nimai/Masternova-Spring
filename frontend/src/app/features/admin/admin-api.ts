import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { Role, User } from '../../core/auth/auth-api';

/**
 * /api/v1/admin/users — ADMIN only. Feature-scoped (not core/): only the admin page needs it, so it
 * ships in the admin chunk. The SERVER enforces ADMIN (@PreAuthorize); the route guard is UX only.
 */
@Service()
export class AdminApi {
  private readonly http = inject(HttpClient);

  getUser(id: string): Observable<User> {
    return this.http.get<User>(`/api/v1/admin/users/${encodeURIComponent(id)}`);
  }

  /** Replaces the user's roles (PUT = the full set, not a delta). Mirrors `ChangeRolesRequest`. */
  changeRoles(id: string, roles: Role[]): Observable<User> {
    return this.http.put<User>(`/api/v1/admin/users/${encodeURIComponent(id)}/roles`, { roles });
  }
}
