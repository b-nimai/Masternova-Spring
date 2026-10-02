import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationApi } from './notification-api';

describe('NotificationApi', () => {
  let api: NotificationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(NotificationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the preferences', () => {
    api.preferences().subscribe();
    expect(http.expectOne('/api/v1/me/notification-preferences').request.method).toBe('GET');
  });

  it('PUTs one category', () => {
    api.setPreference('PRODUCT_NEWS', false).subscribe();
    const req = http.expectOne('/api/v1/me/notification-preferences/PRODUCT_NEWS');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ enabled: false });
  });

  it('POSTs the unsubscribe token', () => {
    api.unsubscribe('tok').subscribe();
    const req = http.expectOne('/api/v1/notifications/unsubscribe');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok' });
  });
});
