#!/usr/bin/env bash
# Boots a throwaway Shiori 1.8.0 and runs the client contract tests against it.
# Rebuilds the Shiori test server from source if needed (needs Go installed).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
ENV=$ROOT/.build-env
PORT=${SHIORI_E2E_PORT:-18080}

if [ ! -x "$ENV/shiori" ]; then
  [ -d .refs/shiori ] || git clone --depth 1 --branch v1.8.0 https://github.com/go-shiori/shiori.git .refs/shiori
  (cd .refs/shiori && GOMODCACHE="$ENV/gomodcache" GOCACHE="$ENV/gocache" GOPATH="$ENV/gopath" go build -o "$ENV/shiori" .)
fi

DATA="$ENV/e2e-data"
rm -rf "$DATA" && mkdir -p "$DATA"

SHIORI_DIR="$DATA" \
SHIORI_HTTP_ADDRESS="127.0.0.1:" \
SHIORI_HTTP_PORT="$PORT" \
SHIORI_HTTP_SECRET_KEY=e2e-only \
  "$ENV/shiori" server >"$ENV/e2e-server.log" 2>&1 &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT

for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:$PORT/system/liveness" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done
curl -sf "http://127.0.0.1:$PORT/system/liveness" >/dev/null || {
  echo "shiori did not start; see $ENV/e2e-server.log" >&2
  tail -5 "$ENV/e2e-server.log" >&2
  exit 1
}

MYLINKS_E2E_SERVER="http://127.0.0.1:$PORT" \
  "$ROOT/build.sh" :app:testDebugUnitTest --rerun --tests 'net.reddec.mylinks.ShioriApiE2ETest' "$@"
