-- Curriculum editing (Phase 6.4): positions are renumbered row by row when a section or lecture is
-- moved or removed, so for a moment two rows share a position — although the finished order is
-- legal. A DEFERRABLE INITIALLY DEFERRED unique constraint is checked at COMMIT, when every row has
-- its final position. (NestJS/Prisma couldn't declare one and parked rows at negative positions
-- first; Flyway runs plain SQL.) docs/lld/catalog-authoring.md §7.
ALTER TABLE section DROP CONSTRAINT section_position_uq;
ALTER TABLE section ADD CONSTRAINT section_position_uq
    UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE lecture DROP CONSTRAINT lecture_position_uq;
ALTER TABLE lecture ADD CONSTRAINT lecture_position_uq
    UNIQUE (section_id, position) DEFERRABLE INITIALLY DEFERRED;
