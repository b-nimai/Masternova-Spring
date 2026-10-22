import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

/**
 * "Get the skills you need" (Figma frame 40): the light band with the four-step timeline and the
 * photo carrying a stats banner. The Figma's pillars become Masternova's.
 */
@Component({
  selector: 'app-skills-section',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './skills-section.html',
  styleUrl: './skills-section.scss',
})
export class SkillsSection {
  /** How many subjects (top-level categories) the catalog has: the banner's first number. */
  readonly subjects = input(0);

  protected readonly pillars = [
    {
      icon: 'verified_user',
      title: 'Only buyers watch',
      text: 'Every lecture asks the entitlement engine first',
    },
    { icon: 'school', title: 'Learn from instructors', text: 'Courses built by people who teach' },
    {
      icon: 'history',
      title: 'Resume anywhere',
      text: 'Your place in every video, on every device',
    },
    { icon: 'code', title: 'Solve the problem', text: 'Code your way to a solution' },
  ];
}
