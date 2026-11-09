-- Incremental migration: run against the explicitly selected existing database.
CREATE TABLE IF NOT EXISTS kb_semantic_vector (
  chunk_id BIGINT NOT NULL,
  config_fingerprint CHAR(64) NOT NULL,
  model VARCHAR(100) NOT NULL,
  dimensions INT NOT NULL,
  preprocessing_version VARCHAR(50) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  vector_json MEDIUMTEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (chunk_id, config_fingerprint),
  KEY idx_semantic_config (config_fingerprint),
  CONSTRAINT fk_semantic_chunk FOREIGN KEY (chunk_id) REFERENCES kb_chunk(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
