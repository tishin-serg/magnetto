param(
    [string]$Root = "E:\Movies",
    [string]$HostAddress = "0.0.0.0",
    [int]$Port = 8085
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    throw "WebDAV root does not exist: $Root"
}

$user = $env:WEBDAV_USERNAME
$password = $env:WEBDAV_PASSWORD
if ([string]::IsNullOrWhiteSpace($user) -or [string]::IsNullOrWhiteSpace($password)) {
    throw "WEBDAV_USERNAME and WEBDAV_PASSWORD must be set in the environment"
}

$wsgidav = Join-Path $env:LOCALAPPDATA "Programs\Python\Python313\Scripts\wsgidav.exe"
if (-not (Test-Path -LiteralPath $wsgidav -PathType Leaf)) {
    throw "wsgidav.exe not found: $wsgidav"
}

$safeRoot = $Root.Replace("\", "/")
$configPath = Join-Path $env:TEMP "wsgidav-movies.yaml"
$outLogPath = Join-Path (Get-Location) "webdav-movies.out.log"
$errLogPath = Join-Path (Get-Location) "webdav-movies.err.log"

@"
host: "$HostAddress"
port: $Port
provider_mapping:
  "/": "$safeRoot"
simple_dc:
  user_mapping:
    "*":
      "$user":
        password: "$password"
        roles: ["admin"]
http_authenticator:
  accept_basic: true
  accept_digest: true
  default_to_digest: false
dir_browser:
  enable: true
logging:
  enable_loggers: []
"@ | Set-Content -LiteralPath $configPath -Encoding UTF8

Get-CimInstance Win32_Process |
    Where-Object { $_.CommandLine -like "*wsgidav*8085*" -or $_.CommandLine -like "*wsgidav-movies.yaml*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

$process = Start-Process `
    -FilePath $wsgidav `
    -ArgumentList @("--config", $configPath) `
    -WorkingDirectory (Get-Location) `
    -WindowStyle Hidden `
    -RedirectStandardOutput $outLogPath `
    -RedirectStandardError $errLogPath `
    -PassThru

Start-Sleep -Seconds 3

[pscustomobject]@{
    ProcessId = $process.Id
    Root = $Root
    Host = $HostAddress
    Port = $Port
    OutLog = $outLogPath
    ErrLog = $errLogPath
}
