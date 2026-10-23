import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { CourseSummary } from '../../../core/api/catalog-api';
import { LEVEL_LABELS } from '../../catalog/catalog-labels';
import { PointerGlow } from '../../../shared/motion/pointer-glow';
import { Reveal } from '../../../shared/motion/reveal';

/**
 * "Unlock the Power of Code" (Figma frame 41): three course cards straddling a dark and a light
 * band. The Figma highlights one card; here it's the first, or whichever one is pointed at.
 */
@Component({
  selector: 'app-course-highlights',
  imports: [PointerGlow, Reveal, RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './course-highlights.html',
  styleUrl: './course-highlights.scss',
})
export class CourseHighlights {
  readonly courses = input.required<readonly CourseSummary[]>();
  protected readonly levels = LEVEL_LABELS;
}
