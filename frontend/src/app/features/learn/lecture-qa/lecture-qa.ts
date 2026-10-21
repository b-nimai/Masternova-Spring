import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { of } from 'rxjs';
import {
  AnswerResponse,
  EngagementApi,
  MAX_ANSWER_DEPTH,
  QuestionSummary,
} from '../../../core/api/engagement-api';
import { asProblem } from '../../../core/api/problem';

/**
 * The lecture's Q&A beside the player (Phase 11.5): its questions newest first, asking one, and a
 * thread with replies.
 *
 * ⭐ A thread arrives FLAT in reading order (the server's recursive query), so rendering is one
 *    loop indented by `depth` — no recursive component, no tree to rebuild on the client.
 * ⭐ Every post carries an Idempotency-Key, made when the draft starts and renewed only after the
 *    post succeeds or is REFUSED (a 4xx): a retry after a timeout reuses it and can't post twice,
 *    while a corrected post after a refusal isn't mistaken for the refused one.
 */
@Component({
  selector: 'app-lecture-qa',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './lecture-qa.html',
  styleUrl: './lecture-qa.scss',
})
export class LectureQa {
  private readonly api = inject(EngagementApi);

  readonly lectureId = input.required<string>();

  protected readonly maxDepth = MAX_ANSWER_DEPTH;

  protected readonly questions = signal<QuestionSummary[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly listError = signal<string | null>(null);

  protected readonly askTitle = signal('');
  protected readonly askBody = signal('');
  private askKey = crypto.randomUUID();

  /** The open thread, or none. */
  protected readonly openId = signal<string | null>(null);
  protected readonly thread = rxResource({
    params: () => this.openId() ?? undefined,
    stream: ({ params: id }) => (id ? this.api.thread(id) : of(null)),
  });

  /** The answer being replied to (null = answering the question itself). */
  protected readonly replyTo = signal<string | null>(null);
  protected readonly replyBody = signal('');
  private replyKey = crypto.randomUUID();

  protected readonly posting = signal(false);
  protected readonly postError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const lecture = this.lectureId();
      untracked(() => {
        this.openId.set(null);
        this.load(lecture, null);
      });
    });
  }

  protected showMore(): void {
    const cursor = this.nextCursor();
    if (cursor) {
      this.load(this.lectureId(), cursor);
    }
  }

  protected ask(): void {
    const title = this.askTitle().trim();
    if (!title || this.posting()) {
      return;
    }
    this.posting.set(true);
    this.postError.set(null);
    this.api.ask(this.lectureId(), { title, body: this.askBody().trim() }, this.askKey).subscribe({
      next: (question) => {
        this.posting.set(false);
        this.askKey = crypto.randomUUID();
        this.askTitle.set('');
        this.askBody.set('');
        this.questions.update((list) => [question, ...list]);
      },
      error: (e: unknown) => {
        if (isRefusal(e)) {
          this.askKey = crypto.randomUUID();
        }
        this.failed(e);
      },
    });
  }

  protected open(questionId: string): void {
    this.openId.set(this.openId() === questionId ? null : questionId);
    this.replyTo.set(null);
    this.replyBody.set('');
  }

  protected startReply(answer: AnswerResponse | null): void {
    this.replyTo.set(answer?.id ?? null);
    this.replyBody.set('');
    this.replyKey = crypto.randomUUID();
  }

  protected postReply(): void {
    const questionId = this.openId();
    const body = this.replyBody().trim();
    if (!questionId || !body || this.posting()) {
      return;
    }
    this.posting.set(true);
    this.postError.set(null);
    this.api.answer(questionId, { parentAnswerId: this.replyTo(), body }, this.replyKey).subscribe({
      next: () => {
        this.posting.set(false);
        this.replyKey = crypto.randomUUID();
        this.replyBody.set('');
        this.replyTo.set(null);
        this.thread.reload(); // the new answer in its place in reading order
        this.questions.update((list) =>
          list.map((q) => (q.id === questionId ? { ...q, answerCount: q.answerCount + 1 } : q)),
        );
      },
      error: (e: unknown) => {
        if (isRefusal(e)) {
          this.replyKey = crypto.randomUUID();
        }
        this.failed(e);
      },
    });
  }

  private load(lectureId: string, cursor: string | null): void {
    this.listError.set(null);
    this.api.questions(lectureId, cursor).subscribe({
      next: (page) => {
        this.questions.update((list) => (cursor ? [...list, ...page.items] : page.items));
        this.nextCursor.set(page.nextCursor);
      },
      error: (e: unknown) =>
        this.listError.set(
          asProblem(e)?.reason === 'NOT_ENROLLED'
            ? 'Enroll in this course to see and ask questions.'
            : "We couldn't load the questions.",
        ),
    });
  }

  private failed(e: unknown): void {
    this.posting.set(false);
    const problem = asProblem(e);
    this.postError.set(
      problem?.code === 'REPLY_TOO_DEEP'
        ? 'That thread is as deep as it goes: reply higher up.'
        : problem?.reason === 'NOT_ENROLLED'
          ? 'Enroll in this course to take part.'
          : "We couldn't post that. Try again.",
    );
  }
}

/** A 4xx: the server refused THIS post; the next attempt is a different request. */
function isRefusal(e: unknown): boolean {
  return e instanceof HttpErrorResponse && e.status >= 400 && e.status < 500;
}
