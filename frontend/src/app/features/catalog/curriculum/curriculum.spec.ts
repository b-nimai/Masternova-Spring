import { TestBed } from '@angular/core/testing';
import { SectionResponse } from '../../../core/api/catalog-api';
import { Curriculum } from './curriculum';

const SECTIONS: SectionResponse[] = [
  {
    id: 's1',
    title: 'Intro',
    lectures: [{ id: 'l1', title: 'Welcome', kind: 'VIDEO', preview: true, durationSeconds: 90 }],
  },
  {
    id: 's2',
    title: 'Core',
    lectures: [
      { id: 'l2', title: 'Pods', kind: 'VIDEO', preview: false, durationSeconds: 600 },
      { id: 'l3', title: 'Cheat sheet', kind: 'ARTICLE', preview: false, durationSeconds: 0 },
    ],
  },
];

describe('Curriculum', () => {
  it('lists every section with its lecture count, total time and previews', async () => {
    await TestBed.configureTestingModule({
      imports: [Curriculum],
    }).compileComponents();
    const fixture = TestBed.createComponent(Curriculum);
    fixture.componentRef.setInput('sections', SECTIONS);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="section-1"]')?.textContent).toContain(
      '2 lectures · 10m',
    );
    expect(el.querySelectorAll('[data-testid="preview"]')).toHaveLength(1);
    expect(el.textContent).toContain('1:30'); // Welcome, as a clock
  });
});
