CREATE INDEX IF NOT EXISTS pollination_contracts_active_hex_idx
  ON pollination_contracts (region, status, hex_id);
