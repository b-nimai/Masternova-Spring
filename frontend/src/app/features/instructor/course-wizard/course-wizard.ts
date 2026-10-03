import { Component, effect, inject, input, OnInit, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { CourseDetails } from '../../../core/api/authoring-api';
import { CatalogApi, COURSE_LEVELS, CourseLevel } from '../../../core/api/catalog-api';
import { MoneyPipe } from '../../../shared/money-pipe';
import { LEVEL_LABELS } from '../../catalog/catalog-labels';
import { CourseEditorStore } from '../course-editor-store';
import { CurriculumEditor } from '../curriculum-editor/curriculum-editor';

/** ⭐ TYPED reactive form: `form.value.level` is a CourseLevel, not `any`. */
type DetailsForm = FormGroup<{
  title: FormControl<string>;
  subtitle: FormControl<string>;
  description: FormControl<string>;
  categorySlug: FormControl<string>;
  level: FormControl<CourseLevel>;
  language: FormControl<string>;
}>;

/**
 * /instructor/courses/:id/edit — the authoring wizard (6.7): Details (autosaved) → Pricing →
 * Curriculum → Review & submit. Study note: patterns/angular/05-wizard-autosave-drag-drop.md.
 */
@Component({
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatStepperModule,
    MoneyPipe,
    CurriculumEditor,
  ],
  // ⭐ one store PER WIZARD: the steps and the curriculum editor share it (and its version)
  providers: [CourseEditorStore],
  selector: 'app-course-wizard',
  styleUrl: './course-wizard.scss',
  templateUrl: './course-wizard.html',
})
export class CourseWizard implements OnInit {
  protected readonly store = inject(CourseEditorStore);
  protected readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });
  protected readonly levels = COURSE_LEVELS;
  protected readonly levelLabels = LEVEL_LABELS;

  /** :id from the route (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly details: DetailsForm = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    subtitle: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(5000)],
    }),
    categorySlug: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    level: new FormControl<CourseLevel>('BEGINNER', { nonNullable: true }),
    language: new FormControl('en', {
      nonNullable: true,
      validators: [Validators.pattern(/^[a-z]{2}$/)],
    }),
  });

  protected readonly pricing = new FormGroup({
    free: new FormControl(true, { nonNullable: true }),
    rupees: new FormControl(499, {
      nonNullable: true,
      validators: [Validators.min(0), Validators.max(100000)],
    }),
  });

  constructor() {
    // The course (re)loaded → fill the forms WITHOUT triggering an autosave. Only on loads, never
    // after our own saves: re-patching then would fight the user's typing.
    effect(() => {
      this.store.loads(); // the ONLY trigger…
      // …⭐ course() is read UNTRACKED: tracking it would re-run this after every save and put the
      //    saved (older) text back over what the user typed meanwhile (found in review)
      const course = untracked(() => this.store.course());
      if (course) {
        this.details.setValue(
          {
            title: course.title,
            subtitle: course.subtitle ?? '',
            description: course.description,
            categorySlug: course.category.slug,
            level: course.level,
            language: course.language,
          },
          { emitEvent: false },
        );
        this.pricing.setValue(
          {
            free: course.priceMinor === 0,
            rupees: course.priceMinor === 0 ? 499 : course.priceMinor / 100,
          },
          { emitEvent: false },
        );
      }
    });

    // ⭐ AUTOSAVE: debounce typing, skip invalid states and no-op changes, then hand the save to
    //    the store's write QUEUE (concatMap there): saves apply in order, each with the version
    //    the previous one produced. Never switchMap for writes: cancelling an in-flight PUT
    //    cancels only the RESPONSE; the server still applies it and the version moves unseen.
    this.details.valueChanges
      .pipe(
        debounceTime(800),
        filter(() => this.details.valid),
        map(() => this.toDetails()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(),
      )
      .subscribe((details) => this.store.saveDetails(details)); // the store's queue orders writes
  }

  ngOnInit(): void {
    this.store.load(this.id());
  }

  protected confirmPrice(): void {
    const { free, rupees } = this.pricing.getRawValue();
    this.store.confirmPrice(free ? 0 : Math.round(rupees * 100));
  }

  protected onStep(event: StepperSelectionEvent): void {
    if (event.selectedIndex === 3) {
      this.store.loadReadiness(); // the checklist reflects every edit made in the other steps
    }
  }

  protected submit(): void {
    this.store.transition('submit');
  }

  protected withdraw(): void {
    this.store.transition('withdraw');
  }

  private toDetails(): CourseDetails {
    const v = this.details.getRawValue();
    return { ...v, subtitle: v.subtitle.trim() || null };
  }
}
