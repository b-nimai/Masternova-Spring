import { TestBed } from '@angular/core/testing';
import { Skeleton } from './skeleton';

describe('Skeleton', () => {
  it('draws one placeholder per expected item and announces a single "Loading…"', async () => {
    const fixture = TestBed.createComponent(Skeleton);
    fixture.componentRef.setInput('shape', 'card');
    fixture.componentRef.setInput('count', 3);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.getAttribute('role')).toBe('status');
    expect(el.querySelectorAll('.item.card')).toHaveLength(3);
    expect(el.textContent?.trim()).toBe('Loading…');
  });
});
