import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationPreferenceResponse } from '../../../core/api/notification-api';
import { query, text } from '../../../../testing/dom';
import { Notifications } from './notifications';

const PREFERENCES: NotificationPreferenceResponse[] = [
  { category: 'ACCOUNT_SECURITY', enabled: true, mandatory: true },
  { category: 'PURCHASE', enabled: true, mandatory: true },
  { category: 'COURSE_ACTIVITY', enabled: true, mandatory: false },
  { category: 'ENGAGEMENT', enabled: false, mandatory: false },
  { category: 'PRODUCT_NEWS', enabled: true, mandatory: false },
];

describe('Notifications', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Notifications],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function loaded(): Promise<ComponentFixture<Notifications>> {
    const fixture = TestBed.createComponent(Notifications);
    fixture.detectChanges();
    http.expectOne('/api/v1/me/notification-preferences').flush(PREFERENCES);
    await fixture.whenStable();
    return fixture;
  }

  /** The switch button Material renders inside <mat-slide-toggle>. */
  function toggle(fixture: ComponentFixture<Notifications>, category: string): HTMLButtonElement {
    return query<HTMLButtonElement>(fixture, `[data-testid="toggle-${category}"] button`);
  }

  it('shows every category, with mandatory ones locked on', async () => {
    const fixture = await loaded();

    expect(toggle(fixture, 'PURCHASE').disabled).toBe(true);
    expect(toggle(fixture, 'PURCHASE').getAttribute('aria-checked')).toBe('true');
    expect(toggle(fixture, 'ENGAGEMENT').getAttribute('aria-checked')).toBe('false');
    expect(toggle(fixture, 'PRODUCT_NEWS').disabled).toBe(false);
  });

  it('flips the toggle immediately and PUTs in the background', async () => {
    const fixture = await loaded();

    toggle(fixture, 'PRODUCT_NEWS').click();
    await fixture.whenStable();

    // ⭐ optimistic: already off, and locked while the request is in flight
    expect(toggle(fixture, 'PRODUCT_NEWS').getAttribute('aria-checked')).toBe('false');
    expect(toggle(fixture, 'PRODUCT_NEWS').disabled).toBe(true);
    const req = http.expectOne('/api/v1/me/notification-preferences/PRODUCT_NEWS');
    expect(req.request.body).toEqual({ enabled: false });

    req.flush({ category: 'PRODUCT_NEWS', enabled: false, mandatory: false });
    await fixture.whenStable();
    expect(toggle(fixture, 'PRODUCT_NEWS').disabled).toBe(false);
    expect(toggle(fixture, 'PRODUCT_NEWS').getAttribute('aria-checked')).toBe('false');
  });

  it('rolls the toggle back when the server refuses', async () => {
    const fixture = await loaded();

    toggle(fixture, 'PRODUCT_NEWS').click();
    await fixture.whenStable();
    http
      .expectOne('/api/v1/me/notification-preferences/PRODUCT_NEWS')
      .flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(toggle(fixture, 'PRODUCT_NEWS').getAttribute('aria-checked')).toBe('true');
    expect(document.body.textContent).toContain(`Couldn't save "News & tips"`);
  });

  it('offers a retry when loading fails', async () => {
    const fixture = TestBed.createComponent(Notifications);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/me/notification-preferences')
      .flush({ status: 503 }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(text(fixture, 'load-error')).toContain("couldn't load");
    query<HTMLButtonElement>(fixture, '[data-testid="load-error"] button').click();
    http.expectOne('/api/v1/me/notification-preferences').flush(PREFERENCES);
    await fixture.whenStable();
    expect(toggle(fixture, 'PRODUCT_NEWS')).toBeTruthy();
  });
});
