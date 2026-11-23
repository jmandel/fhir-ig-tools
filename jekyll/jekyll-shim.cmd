@echo off
rem Drop-in for `jekyll` on Windows: the IG Publisher runs `cmd /C <jekyll-command> build --destination "<out>"`.
rem Pass this file's full path as -jekyll.
rem
rem Like the Gradle wrapper, this one file is all you need. A release copy pins a shim version and its
rem SHA-256; on first use it downloads that jekyll.jar into %USERPROFILE%\.fhir-ig-jekyll\<version>\,
rem verifies it, and runs it from there afterwards (offline). Which jar runs (first match wins):
rem   %FHIR_IG_JEKYLL_JAR%            an explicit jar (offline machines, development)
rem   jekyll.jar next to this file    (release zip), or target\jekyll.jar / build\jekyll.jar (source checkout)
rem   the pinned release              cached under %FHIR_IG_JEKYLL_HOME% (default %USERPROFILE%\.fhir-ig-jekyll)
rem FHIR_IG_JEKYLL_URL overrides the download URL. Java: %JAVA_HOME%\bin\java.exe if set, else java on
rem PATH (17+). Heap may grow to 75% of RAM; no other sizing flags needed. SHIM_JAVA_OPTS adds JVM options.
setlocal
set "SHIM_VERSION=@SHIM_VERSION@"
set "SHIM_SHA256=@SHIM_SHA256@"

if defined FHIR_IG_JEKYLL_JAR set "SHIM_JAR=%FHIR_IG_JEKYLL_JAR%"
if defined FHIR_IG_JEKYLL_JAR goto run
set "SHIM_JAR=%~dp0jekyll.jar"
if exist "%SHIM_JAR%" goto run
set "SHIM_JAR=%~dp0target\jekyll.jar"
if exist "%SHIM_JAR%" goto run
set "SHIM_JAR=%~dp0build\jekyll.jar"
if exist "%SHIM_JAR%" goto run
if "%SHIM_VERSION:~0,1%"=="@" goto unpinned

if not defined FHIR_IG_JEKYLL_HOME set "FHIR_IG_JEKYLL_HOME=%USERPROFILE%\.fhir-ig-jekyll"
set "SHIM_DIR=%FHIR_IG_JEKYLL_HOME%\%SHIM_VERSION%"
set "SHIM_JAR=%SHIM_DIR%\jekyll.jar"
if exist "%SHIM_JAR%" goto run
if not defined FHIR_IG_JEKYLL_URL set "FHIR_IG_JEKYLL_URL=https://github.com/jmandel/fhir-ig-tools/releases/download/v%SHIM_VERSION%/jekyll.jar"
echo jekyll-shim: downloading jekyll.jar %SHIM_VERSION% from %FHIR_IG_JEKYLL_URL% - first use only 1>&2
rem Download to a temporary file, verify the SHA-256, then move it into place.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12; New-Item -ItemType Directory -Force -Path $env:SHIM_DIR | Out-Null; $t=Join-Path $env:SHIM_DIR ('jekyll.jar.' + $PID + '.part'); try { Invoke-WebRequest -UseBasicParsing -Uri $env:FHIR_IG_JEKYLL_URL -OutFile $t; $h=(Get-FileHash -Algorithm SHA256 -LiteralPath $t).Hash.ToLower(); if ($h -ne $env:SHIM_SHA256) { [Console]::Error.WriteLine('jekyll-shim: checksum mismatch for ' + $env:FHIR_IG_JEKYLL_URL + ': expected ' + $env:SHIM_SHA256 + ', got ' + $h); exit 1 }; Move-Item -Force -LiteralPath $t -Destination $env:SHIM_JAR } catch { [Console]::Error.WriteLine('jekyll-shim: download failed: ' + $_); exit 1 } finally { if (Test-Path -LiteralPath $t) { Remove-Item -Force -LiteralPath $t } }"
if errorlevel 1 exit /b 1

:run
set "SHIM_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "SHIM_JAVA=%JAVA_HOME%\bin\java.exe"
"%SHIM_JAVA%" -XX:MaxRAMPercentage=75 %SHIM_JAVA_OPTS% -jar "%SHIM_JAR%" %*
exit /b %ERRORLEVEL%

:unpinned
echo jekyll-shim: no jekyll.jar next to this file and no pinned version; in a source checkout run ./build.sh, or set FHIR_IG_JEKYLL_JAR 1>&2
exit /b 1
