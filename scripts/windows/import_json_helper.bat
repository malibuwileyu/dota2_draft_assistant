@echo off
REM Helper script to load JSON files into PostgreSQL for Windows
REM Usage: import_json_helper.bat <source_file> <target_file>

REM Get parameters
set SOURCE_FILE=%~1
set TARGET_FILE=%~2

REM Read the file
type "%SOURCE_FILE%" > "%TARGET_FILE%"

exit 0