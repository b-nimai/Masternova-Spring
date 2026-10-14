import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { canWatchGuard } from './can-watch-guard';

describe('canWatchGuard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function run(): Promise<boolean | UrlTree> {
    const route = { paramMap: convertToParamMap({ slug: 'k8s', lectureId: 'l1' }) };
    const result = TestBed.runInInjectionContext(() =>
      canWatchGuard(route as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    return firstValueFrom(result);
  }

  it('lets an allowed viewer in', async () => {
    const decision = run();
    http.expectOne('/api/v1/lectures/l1/access').flush({ kind: 'ALLOWED', grant: 'ENTITLED' });
    expect(await decision).toBe(true);
  });

  it('sends a denied viewer to the course page, saying why', async () => {
    const decision = run();
    http
      .expectOne('/api/v1/lectures/l1/access')
      .flush({ kind: 'DENIED', reason: 'ENTITLEMENT_EXPIRED' });
    expect(String(await decision)).toBe('/courses/k8s?locked=ENTITLEMENT_EXPIRED');
  });

  it('sends an unknown lecture back to the course page', async () => {
    const decision = run();
    http
      .expectOne('/api/v1/lectures/l1/access')
      .flush({ status: 404, code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(String(await decision)).toBe('/courses/k8s');
  });
});
