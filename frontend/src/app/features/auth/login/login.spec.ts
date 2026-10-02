import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { submit, text, type } from '../../../../testing/dom';
import { TokenResponse } from '../../../core/auth/auth-api';
import { Login } from './login';

describe('Login', () => {
  const tokens: TokenResponse = {
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'u1',
      email: 'asha@x.dev',
      displayName: 'Asha',
      roles: ['LEARNER'],
      emailVerified: true,
    },
  };
  let fixture: ComponentFixture<Login>;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function fillAndSubmit(email = 'asha@x.dev', password = 'pw-12345') {
    type(fixture, 'email', email);
    type(fixture, 'password', password);
    submit(fixture);
    await fixture.whenStable();
  }

  it('does not call the API while the form is invalid', async () => {
    await fillAndSubmit('not-an-email', '');
    http.expectNone('/api/v1/auth/login');
  });

  it('logs in and goes to /account by default', async () => {
    await fillAndSubmit();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ email: 'asha@x.dev', password: 'pw-12345' });
    req.flush(tokens);

    expect(navigate).toHaveBeenCalledWith('/account');
  });

  it('returns to a safe returnUrl, but never to another site', async () => {
    fixture.componentRef.setInput('returnUrl', '//evil.example');
    await fillAndSubmit();
    http.expectOne('/api/v1/auth/login').flush(tokens);
    expect(navigate).toHaveBeenCalledWith('/account');

    fixture.componentRef.setInput('returnUrl', '/admin');
    await fillAndSubmit();
    http.expectOne('/api/v1/auth/login').flush(tokens);
    expect(navigate).toHaveBeenLastCalledWith('/admin');
  });

  it('shows ONE generic message for INVALID_CREDENTIALS', async () => {
    await fillAndSubmit();
    http
      .expectOne('/api/v1/auth/login')
      .flush(
        { status: 401, code: 'INVALID_CREDENTIALS' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    expect(text(fixture, 'form-error')).toBe('Email or password is incorrect.');
    expect(navigate).not.toHaveBeenCalled();
  });
});
