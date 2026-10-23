import { Component, input } from '@angular/core';

/** One token of a code line, coloured like the Figma's (keyword, type, attribute, string). */
export interface CodeToken {
  text: string;
  kind?: 'keyword' | 'tag' | 'attr' | 'string';
}

/** The home page's glass code panel over a blurred glow (Figma "HTML_source_code_example"). */
@Component({
  selector: 'app-code-panel',
  templateUrl: './code-panel.html',
  styleUrl: './code-panel.scss',
})
export class CodePanel {
  readonly lines = input.required<readonly CodeToken[][]>();
  readonly glow = input<'brown' | 'blue'>('blue');
}
