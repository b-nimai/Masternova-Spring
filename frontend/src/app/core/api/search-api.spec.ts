import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SearchApi } from './search-api';

describe('SearchApi', () => {
  let api: SearchApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(SearchApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches hybrid by default, query string encoded', () => {
    api.search('c# & .net').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/search/courses');
    expect(req.request.params.get('q')).toBe('c# & .net');
    expect(req.request.params.get('mode')).toBe('HYBRID');
    expect(req.request.params.get('limit')).toBe('20');
  });

  it('asks for suggestions', () => {
    api.suggest('kube').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/search/suggestions');
    expect(req.request.params.get('q')).toBe('kube');
  });
});
