@echo off
setlocal
rem Toolchain: fv-local.cmd in this folder (gitignored) may set JAVA_HOME,
rem GRADLE_USER_HOME and ANDROID_HOME first. The defaults below guess a standard
rem install; override any of them when the guess is wrong.
if exist "%~dp0fv-local.cmd" call "%~dp0fv-local.cmd"
if not defined JAVA_HOME set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo java=MISSING at "%JAVA_HOME%"
    endlocal
    exit /b 1
)
rem Double-clicking this file passes no arguments. Older version printed one usage
rem line and exited, so the window closed before anything could be read and it looked
rem like "the build downloaded nothing". Now no arguments = debug assemble + lint.
set "TASKS=%*"
if "%~1"=="" set "TASKS=:app:assembleDebug :app:lintDebug"
echo tasks=%TASKS%
call "%~dp0gradlew.bat" %TASKS%
set "RC=%ERRORLEVEL%"
rem Hold a double-clicked console open so the result stays readable. Agent and
rem terminal runs set FV_SCRIPTED=1 to skip the pause.
if not defined FV_SCRIPTED pause
endlocal & exit /b %RC%
