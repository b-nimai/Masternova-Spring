import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { CursorPage } from './catalog-api';

/** Mirrors `ReviewResponse`. The author is a name only, never an id. */
export interface ReviewResponse {
  id: string;
  rating: number;
  body: string;
  authorName: string;
  createdAt: string;
  updatedAt: string;
  edited: boolean;
}

/** Mirrors `RatingSummaryResponse`: `stars[0]` = 1★ … `stars[4]` = 5★. */
export interface RatingSummaryResponse {
  average: number;
  count: number;
  stars: number[];
}

/** Mirrors `WriteReviewRequest`. */
export interface WriteReviewRequest {
  rating: number;
  body: string;
}

/** Mirrors `QuestionSummary`. */
export interface QuestionSummary {
  id: string;
  lectureId: string;
  title: string;
  body: string;
  authorName: string;
  byInstructor: boolean;
  answerCount: number;
  createdAt: string;
  lastActivityAt: string;
}

/** Mirrors `AnswerResponse`: a thread is FLAT, in reading order; indent by `depth` (1–3). */
export interface AnswerResponse {
  id: string;
  parentId: string | null;
  depth: number;
  body: string;
  authorName: string;
  byInstructor: boolean;
  createdAt: string;
}

/** Mirrors `QuestionThreadResponse`. */
export interface QuestionThreadResponse {
  question: QuestionSummary;
  answers: AnswerResponse[];
  truncated: boolean;
}

/** How deep a reply may go (the server answers 422 `REPLY_TOO_DEEP` beyond). */
export const MAX_ANSWER_DEPTH = 3;

/** The engagement module's API (API conventions §16): reviews and lecture Q&A. */
@Service()
export class EngagementApi {
  private readonly http = inject(HttpClient);

  reviews(
    courseId: string,
    cursor: string | null,
    limit = 10,
  ): Observable<CursorPage<ReviewResponse>> {
    let params = new HttpParams().set('limit', limit);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<CursorPage<ReviewResponse>>(`/api/v1/courses/${courseId}/reviews`, {
      params,
    });
  }

  ratingSummary(courseId: string): Observable<RatingSummaryResponse> {
    return this.http.get<RatingSummaryResponse>(`/api/v1/courses/${courseId}/reviews/summary`);
  }

  /** 404 when the caller hasn't reviewed the course. */
  myReview(courseId: string): Observable<ReviewResponse> {
    return this.http.get<ReviewResponse>(`/api/v1/me/reviews/${courseId}`);
  }

  /** ⭐ A PUT on the pair: create or rewrite, idempotent by nature (no key needed). */
  writeReview(courseId: string, review: WriteReviewRequest): Observable<ReviewResponse> {
    return this.http.put<ReviewResponse>(`/api/v1/me/reviews/${courseId}`, review);
  }

  deleteReview(courseId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/me/reviews/${courseId}`);
  }

  questions(lectureId: string, cursor: string | null): Observable<CursorPage<QuestionSummary>> {
    const params = cursor ? new HttpParams().set('cursor', cursor) : undefined;
    return this.http.get<CursorPage<QuestionSummary>>(`/api/v1/lectures/${lectureId}/questions`, {
      params,
    });
  }

  /** ⭐ A create with no version: the caller's key makes a retried post post once (§4). */
  ask(
    lectureId: string,
    question: { title: string; body: string },
    idempotencyKey: string,
  ): Observable<QuestionSummary> {
    return this.http.post<QuestionSummary>(`/api/v1/lectures/${lectureId}/questions`, question, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  thread(questionId: string): Observable<QuestionThreadResponse> {
    return this.http.get<QuestionThreadResponse>(`/api/v1/questions/${questionId}`);
  }

  answer(
    questionId: string,
    answer: { parentAnswerId: string | null; body: string },
    idempotencyKey: string,
  ): Observable<AnswerResponse> {
    return this.http.post<AnswerResponse>(`/api/v1/questions/${questionId}/answers`, answer, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
}
