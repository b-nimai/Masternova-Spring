-- Catalog authoring (Phase 6.3): when pricing was confirmed. 0 can't tell "free" from "not decided
-- yet"; the publish gate's PRICE_NOT_SET needs the difference (docs/lld/catalog-authoring.md §3).
ALTER TABLE course ADD COLUMN price_set_at TIMESTAMPTZ;

-- Every course before this migration was created with an explicit price (Phase 5 had no
-- "decide later"), so it counts as priced.
UPDATE course SET price_set_at = created_at;
