import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { submit, text, type } from '../../../testing/dom';
import { User } from '../../core/auth/auth-api';
import { Admin, userRefValidator } from './admin';

describe('Admin', () => {
  const id = '3f6c1a52-8a1e-4b55-9a3e-0d2f8a0b7c11';
  const learner: User = {
    id,
    email: 'b@x.dev',
    displayName: 'Ben',
    roles: ['LEARNER'],
    emailVerified: true,
  };
  let fixture: ComponentFixture<Admin>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Admin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Admin);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/instructor-applications').flush({ items: [], nextCursor: null });
    http.match('/api/v1/meta/ping'); // the status card's ping: not what these tests are about
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function findUser(user = learner) {
    type(fixture, 'who', id);
    submit(fixture);
    http.expectOne(`/api/v1/admin/users/${id}`).flush(user);
    await fixture.whenStable();
  }

  it('userRefValidator accepts an email or a UUID (and empty — that is `required`’s job)', () => {
    const check = (v: string) => userRefValidator(new FormControl(v, { nonNullable: true }));
    expect(check(id)).toBeNull();
    expect(check(' b@x.dev ')).toBeNull();
    expect(check('')).toBeNull();
    expect(check('42')).toEqual({ userRef: true });
  });

  it('rejects something that is neither without calling the API', async () => {
    type(fixture, 'who', 'not-a-user');
    submit(fixture);
    await fixture.whenStable();

    http.expectNone((req) => req.url.startsWith('/api/v1/admin/users'));
    expect(text(fixture, 'who-error')).toContain('email address');
  });

  it('looks an email up by email, not by id', async () => {
    type(fixture, 'who', 'B@x.dev');
    submit(fixture);
    const req = http.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.params.get('email')).toBe('B@x.dev');
    req.flush(learner);
    await fixture.whenStable();

    expect(text(fixture, 'found-user')).toContain('Ben');
  });

  it('finds a user and ticks their current roles', async () => {
    await findUser();
    expect(text(fixture, 'found-user')).toContain('Ben');
    const boxes = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>(
      'mat-checkbox input[type="checkbox"]',
    );
    expect(Array.from(boxes).map((b) => b.checked)).toEqual([true, false, false]);
  });

  it('saves the full role set', async () => {
    await findUser();
    const instructor = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>(
      'mat-checkbox input[type="checkbox"]',
    )[1];
    instructor.click();
    await fixture.whenStable();
    submit(fixture, 1);

    const req = http.expectOne(`/api/v1/admin/users/${id}/roles`);
    expect(req.request.body).toEqual({ roles: ['LEARNER', 'INSTRUCTOR'] });
    req.flush({ ...learner, roles: ['LEARNER', 'INSTRUCTOR'] });
    await fixture.whenStable();

    expect(text(fixture, 'saved')).toBe('Saved.');
  });

  it.each([
    ['NOT_FOUND', 404, 'No user with that email or id.'],
    ['CANNOT_DEMOTE_SELF', 422, "You can't remove your own ADMIN role."],
  ])('maps %s to a readable message', async (code, status, message) => {
    type(fixture, 'who', id);
    submit(fixture);
    http
      .expectOne(`/api/v1/admin/users/${id}`)
      .flush({ status, code }, { status, statusText: 'x' });
    await fixture.whenStable();

    expect(text(fixture, 'admin-error')).toBe(message);
  });
});
