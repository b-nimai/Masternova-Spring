import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { catchError, map, of, startWith } from 'rxjs';
import { MetaApi, Ping } from '../../core/api/meta-api';

/** Union of everything the status card can show — the template switches over `kind`. */
type ApiStatus = { kind: 'loading' } | { kind: 'up'; ping: Ping } | { kind: 'down' };

@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatIconModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly meta = inject(MetaApi);

  /** RxJS stream → signal: the template reads `status()` and re-renders when it changes. */
  protected readonly status = toSignal(
    this.meta.ping().pipe(
      map((ping): ApiStatus => ({ kind: 'up', ping })),
      catchError(() => of<ApiStatus>({ kind: 'down' })),
      startWith<ApiStatus>({ kind: 'loading' }),
    ),
    { requireSync: true },
  );
}
