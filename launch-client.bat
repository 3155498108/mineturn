@echo off
setlocal
title MineTurn AT - Minecraft 1.21.1
pushd "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Java 21 was not found at "%JAVA_HOME%".
    echo Please update JAVA_HOME in this file to your Java 21 installation.
    pause
    popd
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Starting Minecraft 1.21.1 with MineTurn AT...
echo Game files and saves: %CD%\run
echo Keep this window open while playing.
call gradlew.bat runClient --offline --console=plain
set "MC_EXIT=%ERRORLEVEL%"
if not "%MC_EXIT%"=="0" (
    echo.
    echo Startup failed. See the message above and run\logs\latest.log.
    pause
)
popd
exit /b %MC_EXIT%
