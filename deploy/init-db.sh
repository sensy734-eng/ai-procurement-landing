#!/bin/sh
set -eu
# MySQL invokes this only when its dedicated data volume is empty.
# Never run 00_create_database.sql; MYSQL_DATABASE was created by the image entrypoint.
for file in /workbench-db/0[1-8]_*.sql /workbench-db/1[0-4]_*.sql; do
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" < "$file"
done
sh /workbench-deploy/migrate.sh
