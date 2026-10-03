import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDropList,
  CdkDropListGroup,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { LectureKind, LectureResponse, SectionResponse } from '../../../core/api/catalog-api';
import { DurationPipe } from '../../../shared/duration-pipe';
import { CourseEditorStore } from '../course-editor-store';

/**
 * The curriculum editor (6.8). Every change is a COMMAND sent to the server (API conventions §6);
 * the answer — the whole curriculum plus the new version and canUndo/canRedo — replaces the screen.
 *
 * ⭐ Drag-drop (CDK): lectures move across sections. The drop is shown at once (optimistic) and sent
 *    as MOVE_LECTURE; if the server refuses, the store reloads the truth.
 * ⭐ Undo/redo are server-side (the course_edit history), so they work after a reload and from
 *    another tab; the keyboard shortcuts just call them.
 */
@Component({
  imports: [
    FormsModule,
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    DurationPipe,
  ],
  selector: 'app-curriculum-editor',
  styleUrl: './curriculum-editor.scss',
  templateUrl: './curriculum-editor.html',
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class CurriculumEditor {
  protected readonly store = inject(CourseEditorStore);
  protected readonly sections = computed(() => this.store.curriculum()?.sections ?? []);

  protected newSectionTitle = '';
  /** Per-section "add lecture" drafts, keyed by section id. */
  protected readonly drafts: Record<
    string,
    { title: string; kind: LectureKind; minutes: number; preview: boolean }
  > = {};

  protected draft(sectionId: string) {
    return (this.drafts[sectionId] ??= { title: '', kind: 'VIDEO', minutes: 5, preview: false });
  }

  protected addSection(): void {
    const title = this.newSectionTitle.trim();
    if (title) {
      this.store.apply({ kind: 'ADD_SECTION', title });
      this.newSectionTitle = '';
    }
  }

  protected renameSection(section: SectionResponse, title: string): void {
    if (title.trim() && title.trim() !== section.title) {
      this.store.apply({ kind: 'RENAME_SECTION', sectionId: section.id, title: title.trim() });
    }
  }

  protected removeSection(section: SectionResponse): void {
    this.store.apply({ kind: 'REMOVE_SECTION', sectionId: section.id }); // undo brings it back
  }

  /** ⭐ REORDER_SECTIONS sends the WHOLE order: a total operation two tabs can't half-apply. */
  protected moveSection(index: number, by: -1 | 1): void {
    const order = this.sections().map((s) => s.id);
    const target = index + by;
    if (target < 0 || target >= order.length) {
      return;
    }
    [order[index], order[target]] = [order[target], order[index]];
    this.store.apply({ kind: 'REORDER_SECTIONS', order });
  }

  protected addLecture(section: SectionResponse): void {
    const d = this.draft(section.id);
    if (!d.title.trim()) {
      return;
    }
    this.store.apply({
      kind: 'ADD_LECTURE',
      sectionId: section.id,
      title: d.title.trim(),
      lectureKind: d.kind,
      preview: d.preview,
      durationSeconds: Math.max(0, Math.round(d.minutes * 60)),
    });
    this.drafts[section.id] = { title: '', kind: d.kind, minutes: d.minutes, preview: false };
  }

  protected togglePreview(lecture: LectureResponse): void {
    this.store.apply({
      kind: 'UPDATE_LECTURE',
      lectureId: lecture.id,
      title: lecture.title,
      preview: !lecture.preview,
    });
  }

  protected removeLecture(lecture: LectureResponse): void {
    this.store.apply({ kind: 'REMOVE_LECTURE', lectureId: lecture.id });
  }

  /** A lecture dropped into a section (the same one or another). */
  dropLecture(event: CdkDragDrop<SectionResponse, SectionResponse, LectureResponse>): void {
    const from = event.previousContainer.data;
    const to = event.container.data;
    if (from.id === to.id && event.previousIndex === event.currentIndex) {
      return;
    }
    const lecture = event.item.data;
    const current = this.store.curriculum();
    if (current) {
      // ⭐ optimistic: show the move now (immutably), send the command, accept the server's answer
      const sections = current.sections.map((s) => {
        let lectures = s.lectures.filter((l) => l.id !== lecture.id);
        if (s.id === to.id) {
          lectures = [
            ...lectures.slice(0, event.currentIndex),
            lecture,
            ...lectures.slice(event.currentIndex),
          ];
        }
        return { ...s, lectures };
      });
      this.store.showLocally({ ...current, sections });
    }
    this.store.apply({
      kind: 'MOVE_LECTURE',
      lectureId: lecture.id,
      toSectionId: to.id,
      toPosition: event.currentIndex,
    });
  }

  /**
   * Ctrl/⌘+Z undo, Ctrl/⌘+Shift+Z or Ctrl+Y redo. ⭐ Not while typing: inside an input, Ctrl+Z must
   * undo the TEXT, which is the browser's job.
   */
  onKeydown(event: KeyboardEvent): void {
    const target = event.target; // may be the document itself, which isn't an Element
    if (target instanceof Element && target.closest('input, textarea, [contenteditable="true"]')) {
      return;
    }
    if (!(event.ctrlKey || event.metaKey)) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === 'z' && !event.shiftKey) {
      event.preventDefault();
      this.store.undo();
    } else if ((key === 'z' && event.shiftKey) || key === 'y') {
      event.preventDefault();
      this.store.redo();
    }
  }
}
