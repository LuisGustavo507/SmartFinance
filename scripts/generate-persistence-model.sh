#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Local development credentials stay on the HD, never in the repository.
env_file="${SMARTFINANCE_DB_ENV_FILE:-/data/SmartFinance/dev-postgres.env}"
if [[ ! -f "$env_file" ]]; then
    echo "Missing local database credentials: $env_file" >&2
    exit 1
fi

set -a
# The file is created locally by the developer; do not source untrusted files.
# shellcheck disable=SC1090
source "$env_file"
set +a
: "${POSTGRES_DB:?}"
: "${POSTGRES_USER:?}"
: "${POSTGRES_PASSWORD:?}"

export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/$POSTGRES_DB"
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
export SPRING_MAIN_WEB_APPLICATION_TYPE=none
export MAVEN_USER_HOME="${MAVEN_USER_HOME:-/data/SmartFinance/maven-home}"
export TMPDIR="${TMPDIR:-/data/SmartFinance/tmp}"
repo="${SMARTFINANCE_MAVEN_REPO:-/data/SmartFinance/maven-repository}"

mkdir -p "$MAVEN_USER_HOME" "$TMPDIR" "$repo" target
umask 077
properties=target/reverse-engineering.properties
trap 'rm -f "$properties"' EXIT
printf 'hibernate.connection.driver_class=org.postgresql.Driver\nhibernate.connection.url=%s\nhibernate.connection.username=%s\nhibernate.connection.password=%s\nhibernate.default_schema=public\nhibernate.default_catalog=%s\n' \
    "$SPRING_DATASOURCE_URL" "$POSTGRES_USER" "$POSTGRES_PASSWORD" "$POSTGRES_DB" > "$properties"

# Flyway migrates the database before reverse engineering; Hibernate never creates it.
./mvnw -q -Dmaven.repo.local="$repo" spring-boot:run
./mvnw -q -Dmaven.repo.local="$repo" org.hibernate.orm:hibernate-maven-plugin:hbm2java
