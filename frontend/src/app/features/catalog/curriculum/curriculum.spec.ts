import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SectionResponse } from '../../../core/api/catalog-api';
import { AccessResponse } from '../../../core/api/entitlement-api';
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
  async function render(
    access: ReadonlyMap<string, AccessResponse> | null = null,
    completed: ReadonlySet<string> = new Set(),
  ) {
    await TestBed.configureTestingModule({
      imports: [Curriculum],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(Curriculum);
    fixture.componentRef.setInput('sections', SECTIONS);
    fixture.componentRef.setInput('slug', 'k8s');
    fixture.componentRef.setInput('access', access);
    fixture.componentRef.setInput('completed', completed);
    await fixture.whenStable();
    return fixture;
  }

  it('lists every section with its lecture count, total time and previews', async () => {
    const fixture = await render();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="section-1"]')?.textContent).toContain(
      '2 lectures · 10m',
    );
    expect(el.querySelectorAll('[data-testid="preview"]')).toHaveLength(1);
    expect(el.textContent).toContain('1:30'); // Welcome, as a clock
  });

  it('links the open videos to the player and locks the rest', async () => {
    const fixture = await render(
      new Map<string, AccessResponse>([
        ['l1', { kind: 'ALLOWED', grant: 'PREVIEW_LECTURE' }],
        ['l2', { kind: 'DENIED', reason: 'NO_ENTITLEMENT' }],
        ['l3', { kind: 'ALLOWED', grant: 'ENTITLED' }],
      ]),
    );
    const lecture = (id: string) =>
      (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="lecture-${id}"]`)!;

    expect(lecture('l1').querySelector('a')?.getAttribute('href')).toBe('/courses/k8s/lectures/l1');
    expect(lecture('l2').querySelector('a')).toBeNull();
    expect(lecture('l2').textContent).toContain('lock');
    expect(lecture('l3').querySelector('a')).toBeNull(); // an article: no player for it
  });

  it('ticks the lectures the learner has completed', async () => {
    const open: AccessResponse = { kind: 'ALLOWED', grant: 'ENTITLED' };
    const fixture = await render(
      new Map([
        ['l1', open],
        ['l2', open],
      ]),
      new Set(['l1']),
    );
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="lecture-l1"] [data-testid="completed"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="lecture-l2"] [data-testid="completed"]')).toBeNull();
  });
});
