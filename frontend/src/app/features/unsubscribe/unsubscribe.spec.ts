import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { query, text } from '../../../testing/dom';
import { Unsubscribe } from './unsubscribe';

describe('Unsubscribe', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Unsubscribe],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(token?: string) {
    const fixture = TestBed.createComponent(Unsubscribe);
    if (token) {
      fixture.componentRef.setInput('token', token);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('does nothing on load: a link scanner opening the page unsubscribes nobody', async () => {
    const fixture = await open('signed-token');

    http.expectNone('/api/v1/notifications/unsubscribe');
    expect(text(fixture, 'confirm-text')).toContain('Stop receiving');
  });

  it('POSTs the token when the person clicks, then names the category', async () => {
    const fixture = await open('signed-token');

    query<HTMLButtonElement>(fixture, '[data-testid="confirm"]').click();
    await fixture.whenStable();
    const req = http.expectOne('/api/v1/notifications/unsubscribe');
    expect(req.request.body).toEqual({ token: 'signed-token' });
    req.flush({ category: 'PRODUCT_NEWS' });
    await fixture.whenStable();

    expect(text(fixture, 'done')).toContain('News & tips');
  });

  it('explains an invalid or expired link (422)', async () => {
    const fixture = await open('forged');

    query<HTMLButtonElement>(fixture, '[data-testid="confirm"]').click();
    http
      .expectOne('/api/v1/notifications/unsubscribe')
      .flush(
        { status: 422, code: 'UNSUBSCRIBE_TOKEN_INVALID' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(text(fixture, 'invalid')).toContain('invalid or has expired');
  });

  it('says so when the token is missing, and offers no button', async () => {
    const fixture = await open();

    expect(text(fixture, 'missing')).toContain('missing its token');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="confirm"]'),
    ).toBeNull();
  });
});
