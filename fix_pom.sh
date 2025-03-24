#\!/bin/bash

# Create temporary file
TEMP_FILE=$(mktemp)

# Use awk to fix the pom.xml
awk '
BEGIN { fixed = 0; }
/<n>Dota 2 Draft Assistant<\/n>/ {
    if (\!fixed) {
        print "    <name>Dota 2 Draft Assistant</name>";
        fixed = 1;
        next;
    }
}
{ print }
' /mnt/h/Projects/dota2_draft_assistant/pom.xml > "$TEMP_FILE"

# Replace the original file
cp "$TEMP_FILE" /mnt/h/Projects/dota2_draft_assistant/pom.xml
rm "$TEMP_FILE"
