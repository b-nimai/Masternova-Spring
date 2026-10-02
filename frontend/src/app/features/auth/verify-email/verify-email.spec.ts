import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { text } from '../../../../testing/dom';
import { VerifyEmail } from './verify-email';

describe('VerifyEmail', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifyEmail],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(token?: string) {
    const fixture = TestBed.createComponent(VerifyEmail);
    if (token) {
      fixture.componentRef.setInput('token', token); // what ?token=… does through the router
    }
    fixture.detectChanges(); // runs ngOnInit
    return fixture;
  }

  it('posts the token and shows success', async () => {
    const fixture = await open('raw-token');
    expect(text(fixture, 'verifying')).toContain('Verifying');

    const req = http.expectOne('/api/v1/auth/verify-email');
    expect(req.request.body).toEqual({ token: 'raw-token' });
    req.flush(null);
    await fixture.whenStable();

    expect(text(fixture, 'verified')).toContain('verified');
  });

  it('shows invalid for a used or expired token (422)', async () => {
    const fixture = await open('old');
    http
      .expectOne('/api/v1/auth/verify-email')
      .flush(
        { status: 422, code: 'VERIFICATION_TOKEN_INVALID' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(text(fixture, 'invalid')).toContain('invalid or has expired');
  });

  it('does not call the API without a token', async () => {
    const fixture = await open();
    await fixture.whenStable();

    http.expectNone('/api/v1/auth/verify-email');
    expect(text(fixture, 'missing')).toContain('missing');
  });
});
