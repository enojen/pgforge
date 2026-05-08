-- Experiment 1 supporting indexes.
-- entries.from_account_id and entries.to_account_id are FKs but have no index by default.
-- Without these, FK validation on parent UPDATE/DELETE does a seq-scan of entries.
CREATE INDEX IF NOT EXISTS entries_from_account_id_idx ON entries (from_account_id);
CREATE INDEX IF NOT EXISTS entries_to_account_id_idx ON entries (to_account_id);
