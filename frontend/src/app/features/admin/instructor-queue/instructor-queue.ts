import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { asProblem } from '../../../core/api/problem';
import { AdminApi, PendingApplicationResponse } from '../admin-api';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Haptics } from '../../../core/haptics';

/**
 * Instructor applications waiting for a decision, oldest first. Approve grants INSTRUCTOR on the
 * server (same transaction); the applicant's next token refresh carries it. A decided row leaves
 * the list once the server has answered, never before (no optimistic removal of an admin act).
 */
@Component({
  selector: 'app-instructor-queue',
  imports: [Skeleton, DatePipe, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './instructor-queue.html',
  styleUrl: './instructor-queue.scss',
})
export class InstructorQueue {
  private readonly api = inject(AdminApi);
  private readonly haptics = inject(Haptics);

  protected readonly queue = rxResource({ stream: () => this.api.pendingApplications() });
  protected readonly loadingMore = signal(false);
  /** The row being decided: its buttons wait. */
  protected readonly deciding = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** The next page joins the list (the queue is keyset-paged, oldest first). */
  protected showMore(cursor: string): void {
    this.loadingMore.set(true);
    this.api.pendingApplications(cursor).subscribe({
      next: (next) => {
        this.queue.update(
          (page) => page && { items: [...page.items, ...next.items], nextCursor: next.nextCursor },
        );
        this.loadingMore.set(false);
      },
      error: () => {
        this.error.set("Couldn't load more applications.");
        this.loadingMore.set(false);
      },
    });
  }

  protected decide(application: PendingApplicationResponse, decision: 'approve' | 'reject'): void {
    this.deciding.set(application.userId);
    this.error.set(null);
    this.api.decide(application.userId, decision).subscribe({
      next: () => {
        this.haptics.success();
        this.queue.update(
          (page) =>
            page && { ...page, items: page.items.filter((a) => a.userId !== application.userId) },
        );
        this.deciding.set(null);
      },
      error: (e: unknown) => {
        this.error.set(
          asProblem(e)?.code === 'APPLICATION_DECIDED'
            ? `${application.displayName}'s application was already decided.`
            : 'Something went wrong. Please try again.',
        );
        this.queue.reload();
        this.deciding.set(null);
      },
    });
  }
}
