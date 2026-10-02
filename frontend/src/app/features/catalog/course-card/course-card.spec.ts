import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { K8S } from '../../../../testing/catalog-fixtures';
import { query, text } from '../../../../testing/dom';
import { CourseCard } from './course-card';

describe('CourseCard', () => {
  let fixture: ComponentFixture<CourseCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CourseCard],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(CourseCard);
  });

  it('links to the course page and formats money and time', async () => {
    fixture.componentRef.setInput('course', K8S);
    await fixture.whenStable();

    expect(query<HTMLAnchorElement>(fixture, 'a.title').getAttribute('href')).toBe('/courses/k8s');
    expect(text(fixture, 'price')).toBe('₹1,499.00');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('1h 24m');
  });

  it('says Free for a free course', async () => {
    fixture.componentRef.setInput('course', { ...K8S, priceMinor: 0 });
    await fixture.whenStable();

    expect(text(fixture, 'price')).toBe('Free');
  });
});
