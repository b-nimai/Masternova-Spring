import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { catchError, map, of, startWith } from 'rxjs';
import { MetaApi, Ping } from '../../core/api/meta-api';

/** Union of everything the status card can show — the template switches over `kind`. */
type Status = { kind: 'loading' } | { kind: 'up'; ping: Ping } | { kind: 'down' };

/**
 * The backend's health and version (Phase 0's first screen; on the admin page since Phase UI).
 */
@Component({
  selector: 'app-api-status',
  imports: [MatCardModule, MatIconModule],
  templateUrl: './api-status.html',
  styleUrl: './api-status.scss',
})
export class ApiStatus {
  private readonly meta = inject(MetaApi);

  /** RxJS stream → signal: the template reads `status()` and re-renders when it changes. */
  protected readonly status = toSignal(
    this.meta.ping().pipe(
      map((ping): Status => ({ kind: 'up', ping })),
      catchError(() => of<Status>({ kind: 'down' })),
      startWith<Status>({ kind: 'loading' }),
    ),
    { requireSync: true },
  );
}
