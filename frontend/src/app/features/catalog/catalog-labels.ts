import { CourseLevel, CourseSort } from '../../core/api/catalog-api';

/** Presentation copy for the catalog's enums (copy lives in the UI, not in the API). */
export const LEVEL_LABELS: Record<CourseLevel, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
  ALL_LEVELS: 'All levels',
};

export const SORT_LABELS: Record<CourseSort, string> = {
  NEWEST: 'Newest',
  HIGHEST_RATED: 'Highest rated',
  PRICE_LOW: 'Price: low to high',
  PRICE_HIGH: 'Price: high to low',
};
