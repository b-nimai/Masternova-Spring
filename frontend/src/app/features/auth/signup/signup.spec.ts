import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { submit, text, type } from '../../../../testing/dom';
import { Signup } from './signup';

describe('Signup', () => {
  let fixture: ComponentFixture<Signup>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Signup);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function fill(confirm = 'correct-horse') {
    type(fixture, 'email', 'asha@x.dev');
    type(fixture, 'displayName', 'Asha');
    type(fixture, 'password', 'correct-horse');
    type(fixture, 'confirmPassword', confirm);
    await fixture.whenStable();
  }

  it('blocks mismatched passwords on the client (cross-field validator)', async () => {
    await fill('something-else');
    submit(fixture);
    await fixture.whenStable();

    http.expectNone('/api/v1/auth/signup');
    expect(text(fixture, 'mismatch')).toContain("don't match");
  });

  it('sends only the API fields (no confirmPassword) and shows the check-your-inbox state', async () => {
    await fill();
    submit(fixture);
    const req = http.expectOne('/api/v1/auth/signup');
    expect(req.request.body).toEqual({
      email: 'asha@x.dev',
      displayName: 'Asha',
      password: 'correct-horse',
    });
    req.flush({
      id: 'u1',
      email: 'asha@x.dev',
      displayName: 'Asha',
      roles: ['LEARNER'],
      emailVerified: false,
    });
    await fixture.whenStable();

    expect(text(fixture, 'signup-done')).toContain('asha@x.dev');
  });

  it('maps 409 EMAIL_TAKEN onto the email field', async () => {
    await fill();
    submit(fixture);
    http
      .expectOne('/api/v1/auth/signup')
      .flush({ status: 409, code: 'EMAIL_TAKEN' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(fixture, 'email-error')).toBe('An account with this email already exists.');
  });

  it("maps a 400's field errors onto the matching fields", async () => {
    await fill();
    submit(fixture);
    http.expectOne('/api/v1/auth/signup').flush(
      {
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'email', code: 'Email', message: 'must be a well-formed email address' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(text(fixture, 'email-error')).toBe('must be a well-formed email address');
  });
});
