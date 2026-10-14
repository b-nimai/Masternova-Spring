import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { DenialReason, EntitlementApi } from '../../core/api/entitlement-api';

/**
 * The player route's guard: ask the entitlement engine BEFORE the page loads. Allowed → in.
 * Denied → back to the course page with `?locked=<reason>`, where the right call to action waits
 * (buy, renew, support). An unknown lecture → the course page.
 *
 * ⭐ UX only: the server decides again when the player asks for a playback token. A guard that
 *    lies (or a stale tab) still can't play anything.
 */
export const canWatchGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const slug = route.paramMap.get('slug') ?? '';
  const back = (locked?: DenialReason) =>
    router.createUrlTree(['/courses', slug], locked ? { queryParams: { locked } } : {});
  return inject(EntitlementApi)
    .lectureAccess(route.paramMap.get('lectureId') ?? '')
    .pipe(
      // ⭐ narrowing on the union: in the false branch `access` is the DENIED shape, with `reason`
      map((access) => (access.kind === 'ALLOWED' ? true : back(access.reason))),
      catchError(() => of(back())),
    );
};
