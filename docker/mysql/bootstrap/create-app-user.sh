#!/bin/sh
set -eu

case "${MYSQL_DATABASE}" in
  *[!A-Za-z0-9_]*|'')
    echo "MYSQL_DATABASE must contain only letters, digits, and underscores." >&2
    exit 2
    ;;
esac

case "${DB_USERNAME}" in
  *[!A-Za-z0-9_]*|''|root)
    echo "DB_USERNAME must be a non-root identifier containing only letters, digits, and underscores." >&2
    exit 2
    ;;
esac

case "${DB_PASSWORD}" in
  *"'"*|'' )
    echo "DB_PASSWORD must be non-empty and cannot contain a single quote." >&2
    exit 2
    ;;
esac

mysql --protocol=TCP --host=mysql --user=root --execute "
  CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
  ALTER USER '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
  GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${DB_USERNAME}'@'%';
  FLUSH PRIVILEGES;
"
