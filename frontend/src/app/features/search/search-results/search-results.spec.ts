import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SearchResults } from './search-results';

describe('SearchResults', () => {
  let fixture: ComponentFixture<SearchResults>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchResults],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(SearchResults);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function text(testId: string): string | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)?.textContent ?? null;
  }

  it('asks nothing without a query', async () => {
    fixture.detectChanges();
    TestBed.tick(); // a loading resource is a pending task: whenStable would wait for the flush
    http.expectNone((r) => r.url === '/api/v1/search/courses');
  });

  it('searches the query in the url and says when the results are keyword-only', async () => {
    fixture.componentRef.setInput('q', ' kubernetes ');
    fixture.componentRef.setInput('mode', 'nonsense'); // an edited url: the default mode
    fixture.detectChanges();
    TestBed.tick(); // a loading resource is a pending task: whenStable would wait for the flush

    const req = http.expectOne((r) => r.url === '/api/v1/search/courses');
    expect(req.request.params.get('q')).toBe('kubernetes');
    expect(req.request.params.get('mode')).toBe('HYBRID');
    req.flush({ mode: 'KEYWORD', degraded: true, items: [] });
    await fixture.whenStable(); // flushed: nothing pending any more
    fixture.detectChanges();

    expect(text('degraded')).toContain('keyword matches');
    expect(text('no-results')).toContain('No courses match');
  });
});
