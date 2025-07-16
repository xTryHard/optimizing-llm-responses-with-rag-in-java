#!/usr/bin/env bash
set -euo pipefail

# ─── Configuration ───────────────────────────────────────────────────────────────
# (adjust if your services run on different hosts/ports or require different creds)
PG_HOST="localhost"
PG_PORT="5432"
PG_USER="postgres"

OLLAMA_HOST="localhost"
OLLAMA_PORT="11434"
OLLAMA_HEALTH_PATH="/"   # endpoint to hit on Ollama

# ─── Helper ──────────────────────────────────────────────────────────────────────
function log_ok()    { echo -e "[\e[32m OK \e[0m] $1"; }
function log_fail()  { echo -e "[\e[31mFAIL\e[0m] $1"; }

# ─── Check Postgres / PGVector ──────────────────────────────────────────────────
echo "Checking Postgres/PGVector at ${PG_HOST}:${PG_PORT}..."
if command -v pg_isready &>/dev/null; then
  if pg_isready -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" >/dev/null 2>&1; then
    log_ok "Postgres is accepting connections"
  else
    log_fail "Postgres is NOT responding"
  fi
else
  # Fallback: check TCP port
  if nc -z "$PG_HOST" "$PG_PORT"; then
    log_ok "Port $PG_PORT is open (Postgres)"
  else
    log_fail "Cannot reach Postgres on port $PG_PORT"
  fi
fi

# ─── Check Ollama ────────────────────────────────────────────────────────────────
echo
echo "Checking Ollama at http://${OLLAMA_HOST}:${OLLAMA_PORT}${OLLAMA_HEALTH_PATH}..."
if curl --silent --fail "http://${OLLAMA_HOST}:${OLLAMA_PORT}${OLLAMA_HEALTH_PATH}" >/dev/null; then
  log_ok "Ollama HTTP API is up"
else
  log_fail "Ollama did not respond"
fi
