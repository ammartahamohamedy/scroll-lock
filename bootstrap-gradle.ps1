param(
    [string]$GradleVersion = "9.6.0"
)

$ErrorActionPreference = "Stop"
$ExpectedZipSha256 = "bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
$ExpectedWrapperJarSha256 = "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $studioJbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
    if (Test-Path (Join-Path $studioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $studioJbr
        $env:Path = "$(Join-Path $studioJbr 'bin');$env:Path"
    } else {
        throw "Java was not found. Install Android Studio (includes JBR/JDK) or JDK 17+, then rerun this script."
    }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$temp = Join-Path $root ".gradle-bootstrap"
$zip = Join-Path $temp "gradle-$GradleVersion-bin.zip"
$url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

New-Item -ItemType Directory -Force -Path $temp | Out-Null
Write-Host "Downloading official Gradle $GradleVersion..."
Invoke-WebRequest -Uri $url -OutFile $zip

$actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($GradleVersion -eq "9.6.0" -and $actual -ne $ExpectedZipSha256) {
    throw "Gradle ZIP checksum mismatch. Expected $ExpectedZipSha256, got $actual"
}

Write-Host "Checksum OK. Extracting..."
Expand-Archive -Path $zip -DestinationPath $temp -Force
$gradleBat = Join-Path $temp "gradle-$GradleVersion\bin\gradle.bat"

Push-Location $root
try {
    # --no-daemon: avoid leaving a daemon locking JARs under $temp (Windows cleanup fails otherwise).
    & $gradleBat wrapper --gradle-version $GradleVersion --distribution-type bin --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed." }

    if ($GradleVersion -eq "9.6.0") {
        $wrapperJar = Join-Path $root "gradle\wrapper\gradle-wrapper.jar"
        $wrapperHash = (Get-FileHash $wrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($wrapperHash -ne $ExpectedWrapperJarSha256) {
            throw "Gradle wrapper JAR checksum mismatch. Expected $ExpectedWrapperJarSha256, got $wrapperHash"
        }

        $wrapperProps = Join-Path $root "gradle\wrapper\gradle-wrapper.properties"
        $content = Get-Content $wrapperProps | Where-Object { $_ -notmatch '^distributionSha256Sum=' }
        $content += "distributionSha256Sum=$ExpectedZipSha256"
        Set-Content -Path $wrapperProps -Value $content -Encoding ASCII
    }
} finally {
    Pop-Location
}

Remove-Item -Recurse -Force $temp
Write-Host "Gradle wrapper created. Next: .\gradlew.bat --version"
