<#
.SYNOPSIS
  Checks that Postgres/PGVector and Ollama are running.

.DESCRIPTION
  1) Uses pg_isready for Postgres (or falls back to pure-TCP via .NET).
  2) Hits Ollama’s HTTP endpoint (or falls back to pure-TCP via .NET).
  Prints [OK], [FAIL] or [WARN] in color.
#>

param(
  [string]$PGHost          = 'localhost',
  [int]   $PGPort          = 5432,
  [string]$PGUser          = 'postgres',

  [string]$OllamaHost      = 'localhost',
  [int]   $OllamaPort      = 11434,
  [string]$OllamaHealthUri = '/'    # endpoint to check Ollama’s health
)

function Log-Ok   { param($m) Write-Host "[OK]   $m" -ForegroundColor Green }
function Log-Fail { param($m) Write-Host "[FAIL] $m" -ForegroundColor Red   }
function Log-Warn { param($m) Write-Host "[WARN] $m" -ForegroundColor Yellow }

# ─── Pure-TCP check using .NET (works on macOS/Linux/Windows) ────────────────
function Test-TcpConnection {
    param(
      [string]$computerName,
      [int]   $port,
      [int]   $timeoutMs = 2000
    )

    try {
      $client = New-Object System.Net.Sockets.TcpClient
      $iar    = $client.BeginConnect($computerName, $port, $null, $null)
      if (-not $iar.AsyncWaitHandle.WaitOne($timeoutMs)) {
        $client.Close()
        return $false
      }
      $client.EndConnect($iar)
      $client.Close()
      return $true
    }
    catch {
      return $false
    }
}

# ─── 1) Check Postgres/PGVector ────────────────────────────────────────────────
Write-Host "Checking Postgres/PGVector at $PGHost`:$PGPort..."
if (Get-Command pg_isready -ErrorAction SilentlyContinue) {
  & pg_isready -h $PGHost -p $PGPort -U $PGUser | Out-Null
  if ($LASTEXITCODE -eq 0) {
    Log-Ok "Postgres is accepting connections"
  } else {
    Log-Fail "Postgres is NOT responding"
  }
} else {
  if (Test-TcpConnection -computerName $PGHost -port $PGPort) {
    Log-Ok "Port $PGPort is open (Postgres)"
  } else {
    Log-Fail "Cannot reach Postgres on port $PGPort"
  }
}

# ─── 2) Check Ollama ───────────────────────────────────────────────────────────
$url = "http://$OllamaHost`:$OllamaPort$OllamaHealthUri"
Write-Host "`nChecking Ollama at $url..."
try {
  # HTTP-level check
  Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 | Out-Null
  Log-Ok "Ollama HTTP API is up"
}
catch {
  # fallback to TCP ping
  if (Test-TcpConnection -computerName $OllamaHost -port $OllamaPort) {
      Log-Fail "Ollama HTTP API did not respond (port is open)"
  } else {
      Log-Fail "Cannot reach Ollama on port $OllamaPort"
  }
}
