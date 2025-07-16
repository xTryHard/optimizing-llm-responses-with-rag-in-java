#!/usr/bin/env bash
set -euo pipefail

# ─── Configuration ──────────────────────────────────────────────────────────────
PG_HOST="localhost"
PG_PORT="5432"
PG_USER="postgres"

OLLAMA_HOST="localhost"
OLLAMA_PORT="11434"
OLLAMA_HEALTH_PATH="/"   # endpoint to check Ollama’s health

# ─── Helpers ───────────────────────────────────────────────────────────────────
function log_ok()   { echo -e "[\e[32m OK   \e[0m] $1"; }
function log_fail() { echo -e "[\e[31mFAIL \e[0m] $1"; }
function log_warn() { echo -e "[\e[33mWARN \e[0m] $1"; }

function http_check() {
  local url=$1
  if command -v curl &>/dev/null; then
    curl --silent --fail "$url" &>/dev/null && return 0 || return 1
  fi
  if command -v wget &>/dev/null; then
    wget --quiet --spider "$url" &>/dev/null && return 0 || return 1
  fi
  if command -v nc &>/dev/null; then
    nc -z "$OLLAMA_HOST" "$OLLAMA_PORT" >/dev/null 2>&1 && return 0 || return 1
  fi
  return 2
}

# ─── 1) Check Postgres / PGVector ───────────────────────────────────────────────
echo "Checking Postgres/PGVector at ${PG_HOST}:${PG_PORT}..."
if command -v pg_isready &>/dev/null; then
  if pg_isready -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" >/dev/null 2>&1; then
    log_ok "Postgres is accepting connections"
  else
    log_fail "Postgres is NOT responding"
  fi
else
  if nc -z "$PG_HOST" "$PG_PORT" >/dev/null 2>&1; then
    log_ok "Port $PG_PORT is open (Postgres)"
  else
    log_fail "Cannot reach Postgres on port $PG_PORT"
  fi
fi

# ─── 2) Check Ollama ────────────────────────────────────────────────────────────
echo
URL="http://${OLLAMA_HOST}:${OLLAMA_PORT}${OLLAMA_HEALTH_PATH}"
echo "Checking Ollama at $URL..."

if http_check "$URL"; then
  log_ok "Ollama HTTP API is up"
else
  rc=$?
  if [[ $rc -eq 1 ]]; then
    log_fail "Ollama did not respond"
  else
    log_warn "HTTP check skipped (no curl, wget or nc found)"
  fi
fi
