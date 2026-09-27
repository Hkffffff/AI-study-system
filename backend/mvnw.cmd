@echo off
rem Minimal Maven wrapper for Windows cmd/PowerShell: downloads the Maven version pinned in
rem .mvn\wrapper\maven-wrapper.properties into %USERPROFILE%\.m2\wrapper and runs it.
setlocal
set "BASE_DIR=%~dp0"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MVN_CMD="

for /f "usebackq delims=" %%i in (`powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$line = Get-Content '%BASE_DIR%.mvn\wrapper\maven-wrapper.properties' | Where-Object { $_ -like 'distributionUrl=*' } | Select-Object -First 1;" ^
  "$url = ($line -replace '^distributionUrl=', '').Trim() -replace 'tar\.gz$', 'zip';" ^
  "$name = [IO.Path]::GetFileName($url) -replace '-bin\.zip$', '';" ^
  "$dir = Join-Path '%MAVEN_USER_HOME%' ('wrapper\dists\' + $name);" ^
  "$mvn = Join-Path $dir ($name + '\bin\mvn.cmd');" ^
  "if (-not (Test-Path $mvn)) {" ^
  "  New-Item -ItemType Directory -Force $dir | Out-Null;" ^
  "  $zip = Join-Path $dir 'download.zip';" ^
  "  [Console]::Error.WriteLine('Downloading ' + $url);" ^
  "  Invoke-WebRequest -UseBasicParsing $url -OutFile $zip;" ^
  "  Expand-Archive $zip $dir -Force;" ^
  "  Remove-Item $zip" ^
  "};" ^
  "$mvn"`) do set "MVN_CMD=%%i"

if "%MVN_CMD%"=="" (
  echo mvnw: failed to prepare Maven 1>&2
  exit /b 1
)

set "MAVEN_PROJECTBASEDIR=%BASE_DIR:~0,-1%"
"%MVN_CMD%" %*
