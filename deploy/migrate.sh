#!/bin/sh
set -eu
# Runs only inside this Compose database container. SQL migrations are forward-only.
: "${MYSQL_DATABASE:?database required}"
: "${MYSQL_ROOT_PASSWORD:?root password required}"
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
export LANG=C.UTF-8
mysql -uroot "$MYSQL_DATABASE" -e 'CREATE TABLE IF NOT EXISTS workbench_schema_migration (filename VARCHAR(180) PRIMARY KEY, sha256 CHAR(64) NOT NULL, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)'
for file in /workbench-db/migrations/*.sql; do
  filename=$(basename "$file")
  digest=$(sha256sum "$file" | cut -d ' ' -f 1)
  existing=$(mysql -uroot -N -B "$MYSQL_DATABASE" -e "SELECT sha256 FROM workbench_schema_migration WHERE filename='$filename'")
  if [ -n "$existing" ]; then
    if [ "$existing" != "$digest" ]; then
      echo "Applied migration changed: $filename. Restore it and add a new migration." >&2
      exit 1
    fi
    continue
  fi
  mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE" < "$file"
  mysql -uroot "$MYSQL_DATABASE" -e "INSERT INTO workbench_schema_migration(filename,sha256) VALUES('$filename','$digest')"
  echo "Applied $filename"
done
