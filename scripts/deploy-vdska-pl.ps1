param(
    [string]$HostName = "vdska_pl",
    [string]$RemoteDir = "/opt/torrentbot",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$jdkHome = Join-Path $env:USERPROFILE "tools\jdk-21"
$javaExe = Join-Path $jdkHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "JDK 21 was not found at $jdkHome. Run scripts\ensure-maven-wrapper.ps1 first."
}

$mvnw = Join-Path $projectRoot "mvnw.cmd"
if (-not (Test-Path -LiteralPath $mvnw)) {
    throw "Maven Wrapper was not found. Run scripts\ensure-maven-wrapper.ps1 first."
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

Push-Location $projectRoot
try {
    if (-not $SkipTests) {
        & $mvnw test
    }
    & $mvnw -DskipTests package

    $archive = Join-Path $env:TEMP "torrentbot-source-local-build.tar.gz"
    if (Test-Path -LiteralPath $archive) {
        Remove-Item -LiteralPath $archive -Force
    }

    tar -czf $archive `
        --exclude="./target" `
        --exclude="./.git" `
        --exclude="./.agents" `
        --exclude="./.codex" `
        --exclude="./.env" `
        --exclude="./*.tar.gz" `
        --exclude="./*.zip" `
        --exclude="./*.jar" `
        .

    scp $archive "${HostName}:/tmp/torrentbot-source-local-build.tar.gz"

    $remoteScript = @"
set -e
ts=\$(date +%Y%m%d-%H%M%S)
mkdir -p /opt/torrentbot-backups "$RemoteDir"
if [ -d "$RemoteDir" ]; then
  tar -czf /opt/torrentbot-backups/torrentbot-before-local-build-\$ts.tar.gz --exclude="$RemoteDir/.env" -C /opt torrentbot
fi
tar -xzf /tmp/torrentbot-source-local-build.tar.gz -C "$RemoteDir"
cd "$RemoteDir"
docker run --rm -v "${RemoteDir}:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn -B -Dmaven.test.skip=true package
docker build -f Dockerfile.runtime -t torrentbot-bot-app .
docker compose up -d --no-deps bot-app
docker compose ps bot-app
docker exec torrentbot-bot-app-1 sh -c 'wget -qO- http://127.0.0.1:8080/actuator/health || curl -s http://127.0.0.1:8080/actuator/health'
"@

    ssh $HostName $remoteScript
}
finally {
    Pop-Location
}
