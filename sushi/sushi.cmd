@echo off
rem Drop-in for `sushi` (the FSH compiler) on Windows that runs the Java port: put this file on the PATH as
rem sushi.cmd, and the IG Publisher (which runs `cmd /C sushi ...`) or you run it like TypeScript SUSHI 3.21.1.
rem Every argument is passed through unchanged; output and exit status are sushi.jar's.
rem
rem Like the Gradle wrapper, this one file is all you need. A release copy pins a version and the SHA-256 of its
rem sushi.jar; on first use it downloads that jar into %USERPROFILE%\.fhir-ig-sushi\<version>\, verifies it, and
rem runs it from there afterwards (offline). Which jar runs (first match wins):
rem   %FHIR_IG_SUSHI_JAR%             an explicit jar (offline machines, development)
rem   sushi.jar next to this file     (release zip), or target\sushi.jar (source checkout)
rem   the pinned release              cached under %FHIR_IG_SUSHI_HOME% (default %USERPROFILE%\.fhir-ig-sushi)
rem FHIR_IG_SUSHI_URL overrides the download URL. Java: %JAVA_HOME%\bin\java.exe if set, else java on PATH; it
rem must be 17 or newer. SUSHI_JAVA_OPTS adds JVM options.
setlocal
set "SUSHI_VERSION=@SUSHI_VERSION@"
set "SUSHI_SHA256=@SUSHI_SHA256@"

if defined FHIR_IG_SUSHI_JAR set "SUSHI_JAR=%FHIR_IG_SUSHI_JAR%"
if defined FHIR_IG_SUSHI_JAR goto run
set "SUSHI_JAR=%~dp0sushi.jar"
if exist "%SUSHI_JAR%" goto run
set "SUSHI_JAR=%~dp0target\sushi.jar"
if exist "%SUSHI_JAR%" goto run
if "%SUSHI_VERSION:~0,1%"=="@" goto unpinned

if not defined FHIR_IG_SUSHI_HOME set "FHIR_IG_SUSHI_HOME=%USERPROFILE%\.fhir-ig-sushi"
set "SUSHI_DIR=%FHIR_IG_SUSHI_HOME%\%SUSHI_VERSION%"
set "SUSHI_JAR=%SUSHI_DIR%\sushi.jar"
if exist "%SUSHI_JAR%" goto run
if not defined FHIR_IG_SUSHI_URL set "FHIR_IG_SUSHI_URL=https://github.com/jmandel/fhir-ig-tools/releases/download/v%SUSHI_VERSION%/sushi.jar"
echo sushi (fhir-ig-tools wrapper): downloading sushi.jar %SUSHI_VERSION% from %FHIR_IG_SUSHI_URL% - first use only 1>&2
rem Download to a temporary file, verify the SHA-256, then move it into place.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12; New-Item -ItemType Directory -Force -Path $env:SUSHI_DIR | Out-Null; $t=Join-Path $env:SUSHI_DIR ('sushi.jar.' + $PID + '.part'); try { Invoke-WebRequest -UseBasicParsing -Uri $env:FHIR_IG_SUSHI_URL -OutFile $t; $h=(Get-FileHash -Algorithm SHA256 -LiteralPath $t).Hash.ToLower(); if ($h -ne $env:SUSHI_SHA256) { [Console]::Error.WriteLine('sushi (fhir-ig-tools wrapper): checksum mismatch for ' + $env:FHIR_IG_SUSHI_URL + ': expected ' + $env:SUSHI_SHA256 + ', got ' + $h); exit 1 }; Move-Item -Force -LiteralPath $t -Destination $env:SUSHI_JAR } catch { [Console]::Error.WriteLine('sushi (fhir-ig-tools wrapper): download failed: ' + $_); exit 1 } finally { if (Test-Path -LiteralPath $t) { Remove-Item -Force -LiteralPath $t } }"
if errorlevel 1 exit /b 1

:run
set "SUSHI_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "SUSHI_JAVA=%JAVA_HOME%\bin\java.exe"
rem Java 17+: `java -version` prints e.g. openjdk version "21.0.5" ... (or "1.8.0_402" for Java 8)
set "SUSHI_JV="
for /f "tokens=3" %%v in ('call "%SUSHI_JAVA%" -version 2^>^&1 ^| findstr /i /c:" version "') do if not defined SUSHI_JV set "SUSHI_JV=%%~v"
if not defined SUSHI_JV goto nojava
set "SUSHI_JM=0"
for /f "delims=." %%m in ("%SUSHI_JV%") do set /a "SUSHI_JM=%%m" 2>nul
if %SUSHI_JM% LSS 17 goto oldjava
"%SUSHI_JAVA%" %SUSHI_JAVA_OPTS% -jar "%SUSHI_JAR%" %*
exit /b %ERRORLEVEL%

:nojava
echo sushi (fhir-ig-tools wrapper): Java 17+ not found; install it or set JAVA_HOME 1>&2
exit /b 1

:oldjava
echo sushi (fhir-ig-tools wrapper): sushi.jar needs Java 17 or newer, but %SUSHI_JAVA% is version %SUSHI_JV%; set JAVA_HOME to a JDK 17+ 1>&2
exit /b 1

:unpinned
echo sushi (fhir-ig-tools wrapper): no sushi.jar next to this file and no pinned version; in a source checkout run ./build.sh, or set FHIR_IG_SUSHI_JAR 1>&2
exit /b 1
