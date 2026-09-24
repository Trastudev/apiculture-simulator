ALTER TABLE pollination_offers
  ADD COLUMN IF NOT EXISTS claimed_by text;
CREATE INDEX IF NOT EXISTS pollination_offers_claim_idx
  ON pollination_offers (claimed_by, taken, expire_epoch_ms);
