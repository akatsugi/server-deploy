@echo off
setlocal

set "JAVA_HOME=D:\Environment\Java"
set "PATH=%JAVA_HOME%\bin;D:\Environment\apache-maven-3.8.1\bin;%PATH%"

mvn "-Dfile.encoding=UTF-8" "-Dmaven.repo.local=D:\Environment\mavenRepo" clean package dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=target\dependency"

endlocal