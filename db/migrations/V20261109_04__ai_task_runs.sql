-- AI assistance audit only; business writes still use existing explicit user operations.
CREATE TABLE IF NOT EXISTS ai_task_run (
  run_id VARCHAR(36) NOT NULL PRIMARY KEY,
  task_type VARCHAR(40) NOT NULL,
  user_id BIGINT NOT NULL,
  access_fingerprint CHAR(64) NOT NULL,
  object_id BIGINT NULL,
  result_json JSON NOT NULL,
  confirmation_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
