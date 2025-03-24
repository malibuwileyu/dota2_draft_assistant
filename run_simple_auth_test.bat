@echo off
echo Running Simple Steam Authentication Test

rem Compile
call mvn compile test-compile

rem Run the simple test application
call mvn exec:java -Dexec.mainClass="com.dota2assistant.auth.SimpleAuthTest"

echo Test complete