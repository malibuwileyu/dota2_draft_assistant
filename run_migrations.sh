#!/bin/bash
# Script to run database migrations for Dota 2 Draft Assistant

# Database connection parameters
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="dota2_draft_assistant"
DB_USER="dota2_user"
DB_PASSWORD="password"

# Script path
SQL_DIR="src/main/resources/sql"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to run a SQL script
run_script() {
  echo -e "${YELLOW}Running $1...${NC}"
  PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f "$1"
  if [ $? -eq 0 ]; then
    echo -e "${GREEN}Successfully executed $1${NC}"
  else
    echo -e "${RED}Error executing $1${NC}"
    exit 1
  fi
}

# Check if psql is installed
if ! command -v psql &> /dev/null; then
  echo -e "${RED}Error: psql is not installed. Please install PostgreSQL client tools.${NC}"
  exit 1
fi

# Create database if it doesn't exist
echo -e "${YELLOW}Checking if database exists...${NC}"
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -lqt | cut -d \| -f 1 | grep -qw $DB_NAME
if [ $? -ne 0 ]; then
  echo -e "${YELLOW}Database $DB_NAME doesn't exist. Creating it...${NC}"
  PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -c "CREATE DATABASE $DB_NAME;"
  if [ $? -eq 0 ]; then
    echo -e "${GREEN}Database $DB_NAME created successfully.${NC}"
  else
    echo -e "${RED}Error creating database $DB_NAME${NC}"
    exit 1
  fi
else
  echo -e "${GREEN}Database $DB_NAME already exists.${NC}"
fi

# Check current database version
echo -e "${YELLOW}Checking current database version...${NC}"
DB_VERSION=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT MAX(version) FROM db_version;" 2>/dev/null || echo "0")
DB_VERSION=$(echo $DB_VERSION | tr -d '[:space:]')
if [ "$DB_VERSION" = "" ]; then
  DB_VERSION=0
fi

echo -e "${GREEN}Current database version: $DB_VERSION${NC}"

# Apply migrations in order
echo -e "${YELLOW}Applying migrations...${NC}"

# List all migration files in order
MIGRATION_FILES=$(find $SQL_DIR -name "*.sql" | sort)

# Apply migrations
for file in $MIGRATION_FILES; do
  # Extract version number from filename
  FILE_VERSION=$(basename $file | cut -d'_' -f1)
  FILE_VERSION=${FILE_VERSION//[^0-9]/}
  
  # Skip if already applied
  if [ $FILE_VERSION -le $DB_VERSION ] && [ $DB_VERSION -ne 0 ]; then
    echo -e "${GREEN}Skipping $file (already applied)${NC}"
  else
    run_script "$file"
  fi
done

# Verify final database version
FINAL_VERSION=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT MAX(version) FROM db_version;")
FINAL_VERSION=$(echo $FINAL_VERSION | tr -d '[:space:]')

echo -e "${GREEN}Database migrations complete. Current version: $FINAL_VERSION${NC}"
echo -e "${GREEN}You can now run the application with:${NC}"
echo -e "${YELLOW}mvn javafx:run${NC}"