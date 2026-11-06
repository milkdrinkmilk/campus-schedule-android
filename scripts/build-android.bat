@echo off
REM ============================================================
REM  Campus Schedule Android App - local build (Windows)
REM
REM  Usage (from android-app/):
REM    scripts\build-android.bat          build debug APK + unit tests
REM    scripts\build-android.bat clean    clean, then build
REM
REM  Toolchain lookup order:
REM    1. <project>\tools\jdk17 / gradle8
REM    2. <workspace>\.workbuddy\tools\jdk17 / gradle8
REM    3. JAVA_HOME env var (fallback)
REM  JAVA_HOME / GRADLE_BIN / ANDROID_HOME env vars can override.
REM
REM  NOTE: keep this file ASCII-only. cmd.exe decodes .bat with the
REM  system ANSI code page; UTF-8 non-ASCII text breaks parsing.
REM ============================================================

setlocal

set "ROOT=%~dp0.."
set "WORKSPACE=%ROOT%\.."

REM ---- JDK 17 ----
set "JDK_CANDIDATE=%ROOT%\tools\jdk17"
if not exist "%JDK_CANDIDATE%\bin\java.exe" set "JDK_CANDIDATE=%WORKSPACE%\.workbuddy\tools\jdk17"
if not exist "%JDK_CANDIDATE%\bin\java.exe" set "JDK_CANDIDATE=%JAVA_HOME%"
if not exist "%JDK_CANDIDATE%\bin\java.exe" (
    echo [ERROR] JDK 17 not found. Set JAVA_HOME or put jdk17 under tools\.
    exit /b 1
)
set "JAVA_HOME=%JDK_CANDIDATE%"

REM ---- Gradle 8.7 ----
if defined GRADLE_BIN goto :gradle_ready
set "GRADLE_BIN=%ROOT%\tools\gradle8\bin\gradle.bat"
if not exist "%GRADLE_BIN%" set "GRADLE_BIN=%WORKSPACE%\.workbuddy\tools\gradle8\bin\gradle.bat"
:gradle_ready
if not exist "%GRADLE_BIN%" (
    echo [ERROR] Gradle not found. Set GRADLE_BIN or put gradle8 under tools\.
    exit /b 1
)

REM ---- Android SDK (AGP also reads sdk.dir from local.properties) ----
if not defined ANDROID_HOME set "ANDROID_HOME=D:\sdk"
if not exist "%ANDROID_HOME%\platforms\android-34" (
    echo [ERROR] Android SDK 34 not found at %ANDROID_HOME%
    echo Set ANDROID_HOME or write sdk.dir in local.properties.
    exit /b 1
)
if not defined GRADLE_USER_HOME if exist "%WORKSPACE%\.workbuddy\tools\gradle-home" set "GRADLE_USER_HOME=%WORKSPACE%\.workbuddy\tools\gradle-home"

set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%ROOT%"

if "%1"=="clean" (
    echo [1/2] Cleaning...
    call "%GRADLE_BIN%" clean --no-daemon
)

echo [2/2] Building and testing...
call "%GRADLE_BIN%" assembleDebug testDebugUnitTest --no-daemon
if errorlevel 1 (
    echo.
    echo [FAIL] Build failed.
    exit /b 1
)

echo.
echo [OK] Build finished.
echo APK: %ROOT%\app\build\outputs\apk\debug\app-debug.apk
endlocal
