@REM Minimal Maven Wrapper launcher for this project (Windows).
@ECHO OFF

SET MAVEN_PROJECTBASEDIR=%~dp0
IF %MAVEN_PROJECTBASEDIR:~-1%==\ SET MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

SET WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar

IF NOT EXIST "%WRAPPER_JAR%" (
    ECHO Maven wrapper jar not found at %WRAPPER_JAR% 1>&2
    EXIT /B 1
)

SET JAVA_EXE=java
IF NOT "%JAVA_HOME%"=="" SET JAVA_EXE="%JAVA_HOME%\bin\java.exe"

%JAVA_EXE% -classpath "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR% org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%
