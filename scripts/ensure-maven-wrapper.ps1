param(
    [string]$MavenVersion = "3.9.9"
)

$ErrorActionPreference = "Stop"

function Find-ProjectRoot {
    $directory = (Get-Location).Path
    while ($directory) {
        if (Test-Path -LiteralPath (Join-Path $directory "pom.xml")) {
            return $directory
        }
        $parent = Split-Path -Parent $directory
        if ($parent -eq $directory) {
            break
        }
        $directory = $parent
    }
    throw "pom.xml was not found in the current directory or its parents."
}

function Ensure-Maven {
    $toolsDir = Join-Path $env:USERPROFILE "tools"
    $mavenDir = Join-Path $toolsDir "apache-maven-$MavenVersion"
    $mavenCmd = Join-Path $mavenDir "bin\mvn.cmd"
    if (Test-Path -LiteralPath $mavenCmd) {
        return $mavenCmd
    }

    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zipPath = Join-Path $toolsDir "apache-maven-$MavenVersion-bin.zip"
    $url = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
    curl.exe -L --fail -o $zipPath $url
    Expand-Archive -LiteralPath $zipPath -DestinationPath $toolsDir -Force

    if (-not (Test-Path -LiteralPath $mavenCmd)) {
        throw "Maven was downloaded, but $mavenCmd was not found."
    }
    return $mavenCmd
}

function Resolve-Jdk21 {
    $portableJdk = Join-Path $env:USERPROFILE "tools\jdk-21"
    $portableJava = Join-Path $portableJdk "bin\java.exe"
    if (Test-Path -LiteralPath $portableJava) {
        return $portableJdk
    }

    if ($env:JAVA_HOME) {
        $java = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path -LiteralPath $java) {
            $versionOutput = & $java -version 2>&1 | Out-String
            if ($versionOutput -match '"21\.') {
                return $env:JAVA_HOME
            }
        }
    }

    throw "JDK 21 was not found. Install it to $portableJdk or set JAVA_HOME to JDK 21."
}

$projectRoot = Find-ProjectRoot
$mavenCmd = Ensure-Maven
$jdkHome = Resolve-Jdk21

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$(Split-Path -Parent $mavenCmd);$env:Path"

Push-Location $projectRoot
try {
    & $mavenCmd -B wrapper:wrapper
    & (Join-Path $projectRoot "mvnw.cmd") -version
}
finally {
    Pop-Location
}
