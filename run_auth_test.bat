@echo off
echo Running Steam Authentication E2E Test

rem Compile and package the application
call mvn compile test-compile

rem Run the test application
call mvn exec:java -Dexec.mainClass="com.dota2assistant.auth.SteamAuthE2ETest" -Dexec.classpathScope=test

echo Test complete