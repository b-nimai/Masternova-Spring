import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminApi } from './admin-api';

describe('AdminApi', () => {
  let api: AdminApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AdminApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets a user by id', () => {
    api.getUser('u1').subscribe();
    expect(http.expectOne('/api/v1/admin/users/u1').request.method).toBe('GET');
  });

  it('PUTs the full role set', () => {
    api.changeRoles('u1', ['LEARNER', 'INSTRUCTOR']).subscribe();
    const req = http.expectOne('/api/v1/admin/users/u1/roles');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roles: ['LEARNER', 'INSTRUCTOR'] });
  });
});
