@echo off
setlocal

cd /d "%~dp0"
set "JAVA_CMD=java"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

"%JAVA_CMD%" -Dflatlaf.useNativeLibrary=false -Dflatlaf.useSystemFileChooser=false -jar "%~dp0LoongSim.jar" %*
if errorlevel 1 pause
