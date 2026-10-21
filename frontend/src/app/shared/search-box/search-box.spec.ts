import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SearchBox, SUGGEST_DEBOUNCE_MS } from './search-box';

describe('SearchBox', () => {
  let fixture: ComponentFixture<SearchBox>;
  let http: HttpTestingController;
  let input: HTMLInputElement;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [SearchBox],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(SearchBox);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('[data-testid="search-input"]');
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  function type(text: string): void {
    input.value = text;
    input.dispatchEvent(new Event('input'));
  }

  it('waits for a pause in typing, then asks once', () => {
    type('k');
    type('ku');
    type('kub');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS - 1);
    http.expectNone((r) => r.url === '/api/v1/search/suggestions');

    vi.advanceTimersByTime(1);
    const req = http.expectOne((r) => r.url === '/api/v1/search/suggestions');
    expect(req.request.params.get('q')).toBe('kub');
  });

  it('cancels the previous request when the text changes (switchMap)', () => {
    type('kub');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    const first = http.expectOne((r) => r.params.get('q') === 'kub');

    type('kubernetes');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    const second = http.expectOne((r) => r.params.get('q') === 'kubernetes');

    expect(first.cancelled).toBe(true); // ⭐ the old answer can never land
    expect(second.cancelled).toBe(false);
    second.flush([{ slug: 'k8s', title: 'Kubernetes in Practice' }]);
  });

  it('never asks for one character, nor twice for the same text', () => {
    type('k');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    http.expectNone((r) => r.url === '/api/v1/search/suggestions');

    type('go ');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    http.expectOne((r) => r.params.get('q') === 'go').flush([]);
    type('go'); // trims to the same text
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    http.expectNone((r) => r.url === '/api/v1/search/suggestions');
  });

  it('keeps suggesting after a failed request', () => {
    type('kub');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    http
      .expectOne((r) => r.params.get('q') === 'kub')
      .flush('down', { status: 503, statusText: 'Unavailable' });

    type('kube');
    vi.advanceTimersByTime(SUGGEST_DEBOUNCE_MS);
    http.expectOne((r) => r.params.get('q') === 'kube').flush([]);
  });
});
