import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { of } from 'rxjs';
import { CartStore } from '../cart-store';
import { CourseCatalog, CourseSummary } from '../course-catalog';
import { Playground } from './playground';

describe('Playground (container component)', () => {
  const courses: CourseSummary[] = [
    { id: 'a', title: 'Cheap', category: 'Backend', priceMinor: 100, rating: 4 },
    { id: 'b', title: 'Pricey', category: 'Backend', priceMinor: 900, rating: 5 },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Playground],
      // ⭐ swap the real service for a stub: answers instantly, no network
      providers: [{ provide: CourseCatalog, useValue: { search: () => of(courses) } }],
    }).compileComponents();
  });

  function type(el: HTMLElement, text: string) {
    const input = el.querySelector<HTMLInputElement>('[data-testid="search"]')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
  }

  it('starts idle', async () => {
    const fixture = TestBed.createComponent(Playground);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Type at least 2 characters',
    );
  });

  it('searches after the debounce and sorts by price', async () => {
    const fixture = TestBed.createComponent(Playground);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    type(el, 'backend');
    await new Promise((resolve) => setTimeout(resolve, 350)); // the real 300 ms debounce
    await fixture.whenStable();

    const titles = Array.from(el.querySelectorAll('app-course-card mat-card-title')).map(
      (t) => t.textContent,
    );
    expect(titles).toEqual(['Cheap', 'Pricey']); // asc by default
  });

  it('the effect keeps the tab title in sync with the cart', async () => {
    const fixture = TestBed.createComponent(Playground);
    await fixture.whenStable();

    TestBed.inject(CartStore).add(courses[0]);
    await fixture.whenStable(); // effects run during change detection

    expect(TestBed.inject(Title).getTitle()).toBe('Masternova (1 in cart)');
  });
});
