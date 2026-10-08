#!/bin/sh
# Alternative to Testcontainers: a disposable local PostgreSQL 18 cluster.
set -eu
cd "$(dirname "$0")/.."
PG_BIN=${PG_BIN:-/opt/homebrew/opt/postgresql@18/bin}
TEST_DIR=$(mktemp -d /private/tmp/zaikou-pg-test.XXXXXX)
TEST_PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
cleanup() {
  "$PG_BIN/pg_ctl" -D "$TEST_DIR/data" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT INT TERM
"$PG_BIN/initdb" -D "$TEST_DIR/data" -U testuser --auth=trust --no-locale --encoding=UTF8 --wal-segsize=1 >"$TEST_DIR/init.log"
"$PG_BIN/pg_ctl" -D "$TEST_DIR/data" -l "$TEST_DIR/server.log" -o "-h 127.0.0.1 -p $TEST_PORT -k $TEST_DIR" -w start
"$PG_BIN/createdb" -h 127.0.0.1 -p "$TEST_PORT" -U testuser zaikou_test
ZAIKOU_TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:$TEST_PORT/zaikou_test" ./gradlew --no-daemon test bootJar
