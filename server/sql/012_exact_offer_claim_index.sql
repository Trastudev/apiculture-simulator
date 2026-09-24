CREATE INDEX IF NOT EXISTS pollination_offers_exact_claim_idx
  ON pollination_offers (hex_id, band, flora, start_doy, taken, expire_epoch_ms);
