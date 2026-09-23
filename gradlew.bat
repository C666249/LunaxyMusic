@echo off
setlocal EnableExtensions
set "DIR=%~dp0"
set "GRADLE_VERSION=9.5.0"
set "DIST_DIR=%USERPROFILE%\.gradle\wrapper\dists\xingyu-gradle-%GRADLE_VERSION%"
set "GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%"
if exist "%GRADLE_HOME%\bin\gradle.bat" goto run
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
echo [SunflowerMusic] First build: downloading Gradle %GRADLE_VERSION% ...
for /L %%N in (1,1,3) do (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13; Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST_DIR%\gradle.zip'; Expand-Archive -Path '%DIST_DIR%\gradle.zip' -DestinationPath '%DIST_DIR%' -Force; Remove-Item '%DIST_DIR%\gradle.zip' -Force" && goto run
  echo [SunflowerMusic] Download attempt %%N failed. Retrying...
  timeout /t 2 /nobreak >nul
)
echo [SunflowerMusic] Gradle download failed. Open the project in Android Studio and Sync once, then retry.
exit /b 1
:run
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
