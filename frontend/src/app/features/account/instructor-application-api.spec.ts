import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InstructorApplicationApi } from './instructor-application-api';

describe('InstructorApplicationApi', () => {
  let api: InstructorApplicationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InstructorApplicationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads and files the own application', () => {
    api.mine().subscribe();
    expect(http.expectOne('/api/v1/me/instructor-application').request.method).toBe('GET');

    api.apply('Kubernetes').subscribe();
    const req = http.expectOne('/api/v1/me/instructor-application');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ pitch: 'Kubernetes' });
  });
});
