-- Idempotency (Phase 2.6) follow-up, found in 5.6: a replayed 201 must carry the same Location
-- header as the original, or the client can't find what it created. Stored next to the body.
ALTER TABLE idempotency_record ADD COLUMN response_location VARCHAR(2048);
