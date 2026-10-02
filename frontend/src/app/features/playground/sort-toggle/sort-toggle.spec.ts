import { TestBed } from '@angular/core/testing';
import { SortToggle } from './sort-toggle';

describe('SortToggle (model two-way binding)', () => {
  it('flips the direction and the model signal follows', async () => {
    const fixture = TestBed.createComponent(SortToggle);
    await fixture.whenStable();
    const toggle = fixture.componentInstance;

    expect(toggle.direction()).toBe('asc');
    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    expect(toggle.direction()).toBe('desc');
  });
});
