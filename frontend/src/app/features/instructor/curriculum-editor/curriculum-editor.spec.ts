import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { LectureResponse, SectionResponse } from '../../../core/api/catalog-api';
import { course, curriculum } from '../../../../testing/authoring-fixtures';
import { query, text } from '../../../../testing/dom';
import { CourseEditorStore } from '../course-editor-store';
import { CurriculumEditor } from './curriculum-editor';

describe('CurriculumEditor', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CurriculumEditor>;
  const url = '/api/v1/instructor/courses/c1/curriculum';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CurriculumEditor],
      providers: [
        CourseEditorStore, // in the app the wizard provides it; here the test does
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(true) }) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(CourseEditorStore).load('c1');
    http.expectOne('/api/v1/instructor/courses/c1').flush(course({ version: 3 }));
    http.expectOne(url).flush(curriculum());
    fixture = TestBed.createComponent(CurriculumEditor);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function command() {
    return http.expectOne(url);
  }

  it('shows every section and lecture with the totals', () => {
    expect(text(fixture, 'totals')).toBe('3 lectures · 15m');
    expect(query(fixture, '[data-testid="lecture-Pods"]')).toBeTruthy();
  });

  it('sends a new section as a command with the current version', async () => {
    const input = query<HTMLInputElement>(fixture, '[data-testid="new-section"]');
    input.value = 'Bonus';
    input.dispatchEvent(new Event('input'));
    query<HTMLButtonElement>(fixture, '[data-testid="add-section"]').click();

    const req = command();
    expect(req.request.body).toEqual({
      expectedVersion: 3,
      command: { kind: 'ADD_SECTION', title: 'Bonus' },
    });
    req.flush(curriculum({ version: 4 }));
  });

  it('reorders sections by sending the whole new order', () => {
    query<HTMLButtonElement>(fixture, '[data-testid="down-0"]').click();

    expect(command().request.body.command).toEqual({
      kind: 'REORDER_SECTIONS',
      order: ['s2', 's1'],
    });
  });

  it('turns a drop into MOVE_LECTURE and shows it before the server answers', async () => {
    const editor = fixture.componentInstance;
    const [intro, core] = curriculum().sections;
    const pods = core.lectures[0];

    editor.dropLecture({
      item: { data: pods },
      previousContainer: { data: core },
      container: { data: intro },
      previousIndex: 0,
      currentIndex: 1,
    } as unknown as CdkDragDrop<SectionResponse, SectionResponse, LectureResponse>);
    await fixture.whenStable();

    // ⭐ optimistic: already in Intro, between Welcome and Setup
    expect(
      Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll(
          '[data-testid="section-0"] li .lecture-title',
        ),
      ).map((e) => e.textContent?.trim()),
    ).toEqual(['Welcome', 'Pods', 'Setup']);
    expect(command().request.body.command).toEqual({
      kind: 'MOVE_LECTURE',
      lectureId: 'l3',
      toSectionId: 's1',
      toPosition: 1,
    });
  });

  it('undoes with the button and with Ctrl+Z, but leaves Ctrl+Z in a text field alone', () => {
    query<HTMLButtonElement>(fixture, '[data-testid="undo"]').click();
    http.expectOne(`${url}/undo`).flush(curriculum({ version: 4, canUndo: true, canRedo: true }));

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true }));
    http.expectOne(`${url}/undo`).flush(curriculum({ version: 5, canUndo: false, canRedo: true }));

    const input = query<HTMLInputElement>(fixture, '[data-testid="new-section"]');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }));
    http.expectNone(`${url}/undo`); // the browser undoes the text

    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, shiftKey: true }),
    );
    expect(http.expectOne(`${url}/redo`).request.body).toEqual({ expectedVersion: 5 });
  });

  it('ignores the shortcuts while another wizard step is shown', async () => {
    fixture.componentRef.setInput('shortcutsEnabled', false);
    await fixture.whenStable();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true }));

    http.expectNone(`${url}/undo`);
  });

  it('disables undo when there is nothing to undo', async () => {
    TestBed.inject(CourseEditorStore).showLocally(curriculum({ canUndo: false }));
    await fixture.whenStable();

    expect(query<HTMLButtonElement>(fixture, '[data-testid="undo"]').disabled).toBe(true);
  });
});
