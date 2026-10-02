import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Shape of `GET /api/v1/meta/ping` — mirrors the backend's `PingResponse` record. */
export interface Ping {
  status: 'UP';
  version: string;
  time: string;
}

/**
 * Talks to the backend's meta endpoints. URLs are relative (`/api/...`): in dev the Angular
 * proxy forwards them to :8080, in the container nginx does — the app never knows the API host.
 */
@Service()
export class MetaApi {
  private readonly http = inject(HttpClient);

  ping(): Observable<Ping> {
    return this.http.get<Ping>('/api/v1/meta/ping');
  }
}
