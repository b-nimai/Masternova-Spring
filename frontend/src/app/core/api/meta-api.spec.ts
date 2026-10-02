import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MetaApi, Ping } from './meta-api';

describe('MetaApi', () => {
  let api: MetaApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(MetaApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the ping endpoint', () => {
    const body: Ping = { status: 'UP', version: '0.0.1', time: '2026-10-02T00:00:00Z' };
    let received: Ping | undefined;

    api.ping().subscribe((ping) => (received = ping));
    http.expectOne({ method: 'GET', url: '/api/v1/meta/ping' }).flush(body);

    expect(received).toEqual(body);
  });
});
