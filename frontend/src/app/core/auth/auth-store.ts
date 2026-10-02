import { computed, inject, Service, signal } from '@angular/core';
import {
  catchError,
  finalize,
  firstValueFrom,
  map,
  Observable,
  shareReplay,
  tap,
  throwError,
} from 'rxjs';
import { AuthApi, Role, TokenResponse, User } from './auth-api';

/**
 * Who is signed in — a signal store (patterns/angular/01 §3) implementing ADR-0006 on the client.
 *
 * ⭐ The access token lives ONLY in memory (this object). Never localStorage: any XSS could read
 *    it there. After a page reload it's gone — restoreSession() gets a new one through the
 *    httpOnly refresh cookie, which JavaScript can't read at all.
 */
@Service()
export class AuthStore {
  private readonly api = inject(AuthApi);

  private readonly accessTokenState = signal<string | null>(null);
  private readonly userState = signal<User | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.userState() !== null);
  readonly roles = computed<readonly Role[]>(() => this.userState()?.roles ?? []);

  /** For the interceptor — a read, never a way to set it from outside. */
  accessToken(): string | null {
    return this.accessTokenState();
  }

  hasAnyRole(roles: readonly Role[]): boolean {
    const mine = this.roles();
    return roles.some((r) => mine.includes(r));
  }

  login(email: string, password: string): Observable<User> {
    return this.api.login(email, password).pipe(
      tap((tokens) => this.apply(tokens)),
      map((tokens) => tokens.user),
    );
  }

  /**
   * ⭐ SINGLE-FLIGHT: however many callers ask at once (several 401s in parallel, two components),
   * only ONE /refresh request is sent and they all share its result. Two parallel refreshes would
   * present the same single-use token twice — and the server treats that as theft and revokes the
   * session (ADR-0006).
   */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.api.refresh().pipe(
      tap((tokens) => this.apply(tokens)),
      map((tokens) => tokens.accessToken),
      catchError((error: unknown) => {
        this.clear(); // the session is gone — become anonymous
        return throwError(() => error);
      }),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false }), // late subscribers get the same answer
    );
    return this.refreshInFlight;
  }

  /** On app start: silently resume the session if the refresh cookie is still valid. */
  restoreSession(): Promise<void> {
    return firstValueFrom(this.refresh()).then(
      () => undefined,
      () => undefined, // no (valid) cookie → simply anonymous; not an error
    );
  }

  /** Ends the session on the server too; the local state is cleared even if that call fails. */
  logout(): Observable<void> {
    return this.api.logout().pipe(finalize(() => this.clear()));
  }

  clear(): void {
    this.accessTokenState.set(null);
    this.userState.set(null);
  }

  private apply(tokens: TokenResponse): void {
    this.accessTokenState.set(tokens.accessToken);
    this.userState.set(tokens.user);
  }
}
