import { TestBed } from '@angular/core/testing';
import { CourseSummary } from '../course-catalog';
import { CourseCard } from './course-card';

describe('CourseCard (inputs and outputs)', () => {
  const java: CourseSummary = {
    id: 'c2',
    title: 'Java Streams',
    category: 'Backend',
    priceMinor: 99_900,
    rating: 4.9,
  };

  async function render(course: CourseSummary, inCart = false) {
    const fixture = TestBed.createComponent(CourseCard);
    fixture.componentRef.setInput('course', course); // ⭐ how a test passes input() values
    fixture.componentRef.setInput('inCart', inCart);
    await fixture.whenStable();
    return fixture;
  }

  it('renders the course and formats the price from minor units', async () => {
    const el = (await render(java)).nativeElement as HTMLElement;

    expect(el.textContent).toContain('Java Streams');
    expect(el.querySelector('[data-testid="price"]')?.textContent).toContain('999.00');
  });

  it('shows Free for a zero price', async () => {
    const el = (await render({ ...java, priceMinor: 0 })).nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="price"]')?.textContent?.trim()).toBe('Free');
  });

  it('emits the course through the output when the button is clicked', async () => {
    const fixture = await render(java);
    const emitted: CourseSummary[] = [];
    fixture.componentInstance.added.subscribe((c) => emitted.push(c));

    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();

    expect(emitted).toEqual([java]);
  });

  it('disables the button when already in the cart', async () => {
    const el = (await render(java, true)).nativeElement as HTMLElement;

    expect(el.querySelector('button')!.disabled).toBe(true);
    expect(el.querySelector('button')!.textContent).toContain('In cart');
  });
});
