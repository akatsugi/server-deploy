@echo off
setlocal

set "JAVA_HOME=D:\Environment\Java"
set "MAVEN_HOME=D:\Environment\apache-maven-3.8.1"
set "MAVEN_REPO=D:\Environment\mavenRepo"
set "APP_NAME=server-deploy"
set "APP_VERSION=1.0.0"
set "MAIN_JAR=%APP_NAME%-%APP_VERSION%.jar"
set "INPUT_DIR=%~dp0target\jpackage-input"
set "DIST_DIR=%~dp0dist"

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

pushd "%~dp0"
call mvn "-Dfile.encoding=UTF-8" "-Dmaven.repo.local=%MAVEN_REPO%" clean package dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=target\dependency"
if errorlevel 1 goto end

if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"
copy /y "target\%MAIN_JAR%" "%INPUT_DIR%\" >nul
copy /y "target\dependency\*.jar" "%INPUT_DIR%\" >nul

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"

"%JAVA_HOME%\bin\jpackage.exe" ^
  --type app-image ^
  --dest "%DIST_DIR%" ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "Akatsugi" ^
  --icon "%~dp0assets\server-deploy-icon.ico" ^
  --runtime-image "%JAVA_HOME%" ^
  --input "%INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class com.akatsugi.serverdeploy.AppStarter
if errorlevel 1 goto end

echo.
echo EXE generated:
echo %DIST_DIR%\%APP_NAME%\%APP_NAME%.exe

:end
popd
endlocal