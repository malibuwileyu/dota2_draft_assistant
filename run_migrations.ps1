param(
    [string]$dbName,
    [string]$dbUser
)

# If parameters are not provided, ask for them
if (-not $dbName) {
    $dbName = Read-Host "Enter database name"
}

if (-not $dbUser) {
    $dbUser = Read-Host "Enter database user"
}

Write-Host "Running database migrations for $dbName as user $dbUser" -ForegroundColor Green

# First apply the new match_players fix
Write-Host "Applying match_players fix..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "ALTER TABLE match_players ADD COLUMN won BOOLEAN DEFAULT FALSE;"

# Check if it exists
Write-Host "Verifying won column exists..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='match_players' AND column_name='won');"

# Check if match_details table exists
Write-Host "Checking match_details table existence..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='match_details');"

# If not, create it
Write-Host "Creating match_details table if it doesn't exist..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "CREATE TABLE IF NOT EXISTS match_details (match_id BIGINT PRIMARY KEY, raw_data TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT NOW(), FOREIGN KEY (match_id) REFERENCES matches(id));"

# Add other required columns
Write-Host "Adding required columns to matches table..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "
ALTER TABLE matches ADD COLUMN IF NOT EXISTS has_details BOOLEAN DEFAULT FALSE;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS lobby_type INTEGER DEFAULT 0;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS patch INTEGER;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS region INTEGER;
"

# Create needed indexes
Write-Host "Creating indexes..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "
CREATE INDEX IF NOT EXISTS idx_match_details_updated_at ON match_details(updated_at);
CREATE INDEX IF NOT EXISTS idx_matches_has_details ON matches(has_details);
"

# Update database version to 8
Write-Host "Updating database version..." -ForegroundColor Cyan
psql -U $dbUser -d $dbName -c "
INSERT INTO db_version (version, description) 
VALUES (8, 'Manual migration fix')
ON CONFLICT (version) DO UPDATE 
SET description = 'Manual migration fix (updated)';
"

# Check database version
Write-Host "Current database version:" -ForegroundColor Green
psql -U $dbUser -d $dbName -c "SELECT * FROM db_version ORDER BY version DESC LIMIT 5;"

Write-Host "Database migration completed!" -ForegroundColor Green