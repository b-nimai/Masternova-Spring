import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Home } from './home';

describe('Home', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function statusText(fixture: ReturnType<typeof TestBed.createComponent<Home>>): string {
    const el = fixture.nativeElement as HTMLElement;
    return el.querySelector('[data-testid="api-status"]')?.textContent ?? '';
  }

  it('shows UP when the API answers', async () => {
    const fixture = TestBed.createComponent(Home);
    http
      .expectOne('/api/v1/meta/ping')
      .flush({ status: 'UP', version: '0.0.1', time: '2026-10-02T00:00:00Z' });
    await fixture.whenStable();

    expect(statusText(fixture)).toContain('API: UP');
  });

  it('shows unreachable when the API fails', async () => {
    const fixture = TestBed.createComponent(Home);
    http.expectOne('/api/v1/meta/ping').flush(null, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(statusText(fixture)).toContain('unreachable');
  });
});
