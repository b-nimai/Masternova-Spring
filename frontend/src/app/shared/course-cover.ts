/** The stock photos the StudyNotion Figma uses on its course cards (`public/covers/`). */
const COVERS = 6;

/**
 * A course's cover picture. The API has no cover image yet, so each course gets one of the
 * Figma's photos, picked by its id: the same course always shows the same picture.
 */
export function coverFor(courseId: string): string {
  let hash = 0;
  for (const char of courseId) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }
  return `covers/cover-${(hash % COVERS) + 1}.jpg`;
}
