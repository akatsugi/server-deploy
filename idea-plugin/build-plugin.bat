@echo off
setlocal

set "JAVA_HOME=D:\Environment\Java"
set "GRADLE_HOME=D:\Environment\gradle-8.14.3"
set "GRADLE_USER_HOME=%~dp0..\.gradle-user-home"
set "PATH=%JAVA_HOME%\bin;%GRADLE_HOME%\bin;%PATH%"

call "%GRADLE_HOME%\bin\gradle.bat" -p "%~dp0" clean buildPlugin
exit /b %ERRORLEVEL%
