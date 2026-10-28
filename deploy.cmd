@echo off
setlocal enabledelayedexpansion
rem FlowVault deploy - deliberately silent about addresses.
rem All adb stdout/stderr goes to nul. This script only prints state words:
rem   tool=OK|MISSING  apk=OK|MISSING  device=READY|NONE  install=OK|FAIL  launch=OK|FAIL
rem
rem device=NONE also covers "unauthorized" and "more than one device" (adb cannot
rem resolve a single target in either case). Read it as "look at the phone screen":
rem unlock it, accept the USB debugging prompt, and in Developer options turn ON the
rem separate "USB install" switch - Honor/MagicOS blocks adb install without it, and
rem that shows up as install=FAIL while device=READY.
rem
rem Wireless path (no USB driver needed):
rem   1) run yourself in any terminal:  adb pair <addr:port> <code>   (platform-tools\adb.exe)
rem   2) put ONLY that addr:port on the first line of  adb-target.txt  (this folder, gitignored)
rem   3) run this script. Delete adb-target.txt to go back to plain USB.
rem
rem Every exit path goes through :end, which pauses so a double-clicked window stays
rem readable. Agent and terminal runs set FV_SCRIPTED=1 to skip the pause.

rem Toolchain: fv-local.cmd (gitignored) may set ANDROID_HOME first.
if exist "%~dp0fv-local.cmd" call "%~dp0fv-local.cmd"
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "TGT=%~dp0adb-target.txt"
set "PKG=com.xiaz.flowvault.debug"
set "RC=0"

if not exist "%ADB%" (
    echo tool=MISSING
    set "RC=1"
    goto end
)
if not exist "%APK%" (
    echo apk=MISSING
    set "RC=1"
    goto end
)
echo tool=OK
echo apk=OK

call "%ADB%" start-server >nul 2>&1

if exist "%TGT%" (
    set /p FVADDR=<"%TGT%"
    call "%ADB%" connect !FVADDR! >nul 2>&1
)

rem get-state succeeds only when exactly one authorized device is reachable.
call "%ADB%" get-state >nul 2>&1
if errorlevel 1 (
    echo device=NONE
    set "RC=1"
    goto end
)
echo device=READY

if defined FVADDR (
    call "%ADB%" -s !FVADDR! install -r -t "%APK%" >nul 2>&1
) else (
    call "%ADB%" install -r -t "%APK%" >nul 2>&1
)
if errorlevel 1 (
    echo install=FAIL
    set "RC=1"
    goto end
)
echo install=OK

if defined FVADDR (
    call "%ADB%" -s !FVADDR! shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
) else (
    call "%ADB%" shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
)
if errorlevel 1 (echo launch=FAIL) else (echo launch=OK)

:end
if not defined FV_SCRIPTED pause
endlocal & exit /b %RC%
