-- Commerce (Phase 9 review): a refund CLAIMS its order before calling the provider, so two
-- concurrent refund requests can't both send money back. NULL = no refund in flight; a claim older
-- than a few minutes is stale (a crashed request) and may be taken over.
ALTER TABLE purchase_order ADD COLUMN refund_requested_at TIMESTAMPTZ;
