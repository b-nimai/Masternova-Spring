import { TestBed } from '@angular/core/testing';
import { StarRating } from './star-rating';

describe('StarRating', () => {
  async function render(value: number, count?: number) {
    const fixture = TestBed.createComponent(StarRating);
    fixture.componentRef.setInput('value', value);
    if (count !== undefined) {
      fixture.componentRef.setInput('count', count);
    }
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const icons = Array.from(el.querySelectorAll('mat-icon')).map((i) =>
      i.classList.contains('empty') ? 'empty' : i.textContent?.trim(),
    );
    return { el, icons };
  }

  it('rounds to the nearest half star', async () => {
    expect((await render(3.7)).icons).toEqual(['star', 'star', 'star', 'star_half', 'empty']);
    expect((await render(4.2)).icons).toEqual(['star', 'star', 'star', 'star', 'empty']);
    expect((await render(5)).icons).toEqual(['star', 'star', 'star', 'star', 'star']);
  });

  it('reads as one sentence, with the count when given', async () => {
    expect((await render(4.5, 12)).el.querySelector('.sr-only')?.textContent).toBe(
      'Rated 4.5 out of 5 by 12 learners',
    );
    expect((await render(3)).el.querySelector('.sr-only')?.textContent).toBe('Rated 3.0 out of 5');
  });
});
