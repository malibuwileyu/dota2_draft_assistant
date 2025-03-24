# PostgreSQL-Only SQL Scripts

This directory contains SQL scripts for initializing and updating the PostgreSQL database schema for the Dota 2 Draft Assistant application.

## Notes

- These scripts are specifically designed for PostgreSQL and will not work with any other database systems
- The application has been modified to work exclusively with PostgreSQL - SQLite support has been removed
- The scripts should be applied in order (001, 002, etc.)
- The database initialization happens automatically when the application starts