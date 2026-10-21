import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureQa } from './lecture-qa';

describe('LectureQa', () => {
  let fixture: ComponentFixture<LectureQa>;
  let http: HttpTestingController;
  const el = (id: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const all = (id: string): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${id}"]`));

  const question = {
    id: 'q1',
    lectureId: 'l1',
    title: 'Why does kubectl hang?',
    body: '',
    authorName: 'Lena',
    byInstructor: false,
    answerCount: 2,
    createdAt: '2026-10-21T10:00:00Z',
    lastActivityAt: '2026-10-21T10:00:00Z',
  };

  function type(testId: string, text: string): void {
    const input = el(testId) as HTMLInputElement;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LectureQa],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(LectureQa);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('lectureId', 'l1');
    fixture.detectChanges();
    http.expectOne('/api/v1/lectures/l1/questions').flush({ items: [question], nextCursor: null });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders a thread flat, indented by depth', async () => {
    el('question')?.click();
    fixture.detectChanges();
    TestBed.tick(); // a loading resource is a pending task: whenStable would wait for the flush
    http.expectOne('/api/v1/questions/q1').flush({
      question,
      truncated: false,
      answers: [
        {
          id: 'a1',
          parentId: null,
          depth: 1,
          body: 'Context?',
          authorName: 'Asha',
          byInstructor: true,
          createdAt: '2026-10-21T10:01:00Z',
        },
        {
          id: 'r1',
          parentId: 'a1',
          depth: 2,
          body: 'Which?',
          authorName: 'Lena',
          byInstructor: false,
          createdAt: '2026-10-21T10:02:00Z',
        },
      ],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const answers = all('answer');
    expect(answers.length).toBe(2);
    expect(answers[0].style.marginLeft).toBe('0rem');
    expect(answers[1].style.marginLeft).toBe('1.5rem');
    expect(answers[0].textContent).toContain('Instructor');
  });

  it('keeps the Idempotency-Key for a retry after a network error, renews it after success', () => {
    type('ask-title', 'Is the code on GitHub?');
    el('ask-submit')?.click();
    const first = http.expectOne((r) => r.method === 'POST');
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error')); // network: the server may have posted it
    fixture.detectChanges();

    el('ask-submit')?.click();
    const retry = http.expectOne((r) => r.method === 'POST');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key); // same post, at most once
    retry.flush({ ...question, id: 'q2', title: 'Is the code on GitHub?', answerCount: 0 });
    fixture.detectChanges();

    expect(all('question').length).toBe(2);
    type('ask-title', 'Another one');
    el('ask-submit')?.click();
    const next = http.expectOne((r) => r.method === 'POST');
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(key);
    next.flush({ ...question, id: 'q3' });
  });

  it('explains a reply that is too deep', async () => {
    el('question')?.click();
    fixture.detectChanges();
    TestBed.tick(); // a loading resource is a pending task: whenStable would wait for the flush
    http.expectOne('/api/v1/questions/q1').flush({ question, truncated: false, answers: [] });
    await fixture.whenStable();
    fixture.detectChanges();

    type('reply-body', 'deeper');
    el('reply-submit')?.click();
    http
      .expectOne('/api/v1/questions/q1/answers')
      .flush({ status: 422, code: 'REPLY_TOO_DEEP' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(el('qa-error')?.textContent).toContain('as deep as it goes');
  });
});
