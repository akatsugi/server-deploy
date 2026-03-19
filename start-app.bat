@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=D:\Environment\Java"
set "PATH=%JAVA_HOME%\bin;D:\Environment\apache-maven-3.8.1\bin;%PATH%"

if not exist "target\classes\com\akatsugi\serverdeploy\AppStarter.class" (
    call "%~dp0package-app.bat"
    if errorlevel 1 exit /b 1
)

java -cp "target\classes;target\dependency\*" com.akatsugi.serverdeploy.AppStarter

endlocal