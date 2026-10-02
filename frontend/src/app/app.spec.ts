import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { App } from './app';

describe('App shell', () => {
  const handset = new BehaviorSubject({ matches: false, breakpoints: {} });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        // ⭐ control the "screen size" from the test
        { provide: BreakpointObserver, useValue: { observe: () => handset } },
      ],
    }).compileComponents();
  });

  it('renders the brand and the navigation from NAV_ITEMS', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.brand')?.textContent).toContain('Masternova');
    expect(
      Array.from(el.querySelectorAll('mat-nav-list a')).map((a) => a.textContent?.trim()),
    ).toEqual(['homeHome', 'sciencePlayground']);
  });

  it('shows the menu button only on handsets', async () => {
    const fixture = TestBed.createComponent(App);
    handset.next({ matches: false, breakpoints: {} });
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-label="Open navigation"]'),
    ).toBeNull();

    handset.next({ matches: true, breakpoints: {} });
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-label="Open navigation"]'),
    ).not.toBeNull();
  });
});
