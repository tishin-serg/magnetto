$ErrorActionPreference = "Stop"

$OllamaExe = "C:\Users\xataa\Documents\Codex\2026-08-28\new-chat-3\work\ollama-standalone\ollama.exe"
$ModelsDir = "C:\Users\xataa\Documents\Codex\2026-08-28\new-chat-3\work\ollama-models"
$TailscaleIp = "100.123.82.79"
$Port = 11434
$LogFile = Join-Path (Split-Path $PSScriptRoot -Parent) "ollama-tailscale-autostart.log"

function Write-Log {
    param([string]$Message)
    try {
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        Add-Content -LiteralPath $LogFile -Value "$timestamp $Message"
    } catch {
    }
}

if (-not (Test-Path -LiteralPath $OllamaExe)) {
    Write-Log "Ollama executable not found: $OllamaExe"
    exit 1
}

if (-not (Test-Path -LiteralPath $ModelsDir)) {
    Write-Log "Ollama models directory not found: $ModelsDir"
    exit 1
}

$tailscaleIp = $TailscaleIp
$ollamaUrl = "http://${tailscaleIp}:$Port/api/tags"

try {
    Invoke-RestMethod -Uri $ollamaUrl -Method Get -TimeoutSec 3 | Out-Null
    Write-Log "Ollama already reachable at $ollamaUrl"
    exit 0
} catch {
    Write-Log "Ollama is not reachable at $ollamaUrl, starting server"
}

Get-Process -Name ollama -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

$processStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processStartInfo.FileName = $OllamaExe
$processStartInfo.Arguments = "serve"
$processStartInfo.WorkingDirectory = Split-Path $OllamaExe
$processStartInfo.UseShellExecute = $false
$processStartInfo.CreateNoWindow = $true
$processStartInfo.EnvironmentVariables["OLLAMA_HOST"] = "${tailscaleIp}:$Port"
$processStartInfo.EnvironmentVariables["OLLAMA_MODELS"] = $ModelsDir

$process = [System.Diagnostics.Process]::Start($processStartInfo)
Write-Log "Started Ollama PID=$($process.Id) OLLAMA_HOST=${tailscaleIp}:$Port OLLAMA_MODELS=$ModelsDir"
