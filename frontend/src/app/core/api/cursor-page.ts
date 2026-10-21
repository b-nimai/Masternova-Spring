/** Mirrors `CursorPage<T>` (platform) — API conventions §2: no total, an opaque cursor or null. */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}
