-- Apply once via the migration ledger; old records retain their original strategy.
ALTER TABLE kb_retrieval_test
  ADD COLUMN actual_strategy VARCHAR(32) NOT NULL DEFAULT 'bm25_hash',
  ADD COLUMN config_id VARCHAR(128) NULL,
  ADD COLUMN retrieval_metadata MEDIUMTEXT NULL;
