import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApi } from './catalog-api';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CatalogApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the facets that are set, repeating level', () => {
    api.browse({ q: 'k8s', level: ['BEGINNER', 'ADVANCED'], price: 'FREE' }, null).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/courses');
    expect(req.request.params.get('q')).toBe('k8s');
    expect(req.request.params.getAll('level')).toEqual(['BEGINNER', 'ADVANCED']);
    expect(req.request.params.get('price')).toBe('FREE');
    expect(req.request.params.get('limit')).toBe('20');
    expect(req.request.params.has('category')).toBe(false);
    expect(req.request.params.has('cursor')).toBe(false); // first page
  });

  it('passes the opaque cursor back untouched', () => {
    api.browse({}, 'TkVXRVNUfDIwMjY', 10).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/courses');
    expect(req.request.params.get('cursor')).toBe('TkVXRVNUfDIwMjY');
    expect(req.request.params.get('limit')).toBe('10');
  });

  it('GETs one course by slug, encoded', () => {
    api.course('k8s basics').subscribe();
    expect(http.expectOne('/api/v1/courses/k8s%20basics').request.method).toBe('GET');
  });

  it('GETs the category tree', () => {
    api.categories().subscribe();
    expect(http.expectOne('/api/v1/categories').request.method).toBe('GET');
  });
});
