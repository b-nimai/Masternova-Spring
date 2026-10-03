import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthoringApi } from '../../../core/api/authoring-api';
import { CatalogApi, COURSE_LEVELS, CourseLevel } from '../../../core/api/catalog-api';
import { asProblem } from '../../../core/api/problem';
import { applyServerErrors } from '../../auth/form-utils';
import { LEVEL_LABELS } from '../../catalog/catalog-labels';

/** /instructor/courses/new — the wizard's first step: just enough to create the DRAFT. */
@Component({
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  selector: 'app-new-course',
  styleUrl: './new-course.scss',
  templateUrl: './new-course.html',
})
export class NewCourse {
  private readonly api = inject(AuthoringApi);
  private readonly router = inject(Router);
  protected readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });
  protected readonly levels = COURSE_LEVELS;
  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  /**
   * ⭐ ONE key per ATTEMPT: a double-click (or a retry after a timeout) sends the SAME key, so the
   * server replays the first 201 instead of creating a second course (API conventions §4). After a
   * refused attempt (4xx — the server stored THAT answer under the key) a corrected form needs a
   * new key, or the server would answer 422 IDEMPOTENCY_KEY_REUSED forever (found in review).
   */
  private idempotencyKey = crypto.randomUUID();

  protected readonly form = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    categorySlug: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    level: new FormControl<CourseLevel>('BEGINNER', { nonNullable: true }),
    language: new FormControl('en', {
      nonNullable: true,
      validators: [Validators.pattern(/^[a-z]{2}$/)],
    }),
  });

  protected create(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failed.set(false);
    this.api
      .create(this.form.getRawValue(), this.idempotencyKey)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (course) => void this.router.navigate(['/instructor/courses', course.id, 'edit']),
        error: (error) => {
          this.idempotencyKey = crypto.randomUUID(); // the next attempt is a NEW request
          if (!applyServerErrors(this.form, asProblem(error))) {
            this.failed.set(true);
          }
        },
      });
  }
}
