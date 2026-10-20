@echo off
setlocal
set "JAVA_HOME=D:\tools\jdk\jdk-17.0.20.1+1"
set "GRADLE_USER_HOME=D:\tools\gradle-home"
set "ANDROID_HOME=D:\tools\android-sdk"
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
