#!/usr/bin/env bash
# Bumps versionCode by 1 and versionName patch (e.g. 1.1.0 -> 1.1.1).
# Usage: ./bump-version.sh [--build]
#   --build  Run ./gradlew assembleDebug after bumping.

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPS="$SCRIPT_DIR/version.properties"

if [ ! -f "$PROPS" ]; then
  echo "version.properties not found at $PROPS"
  exit 1
fi

# Read current
VERSION_CODE=$(grep '^versionCode=' "$PROPS" | cut -d= -f2)
VERSION_NAME=$(grep '^versionName=' "$PROPS" | cut -d= -f2)

# Bump versionCode
NEW_VERSION_CODE=$((VERSION_CODE + 1))

# Bump versionName patch (last number): 1.1.0 -> 1.1.1
IFS='.' read -r -a PARTS <<< "$VERSION_NAME"
LAST_IDX=$((${#PARTS[@]} - 1))
PARTS[$LAST_IDX]=$((${PARTS[$LAST_IDX]} + 1))
NEW_VERSION_NAME=$(IFS='.'; echo "${PARTS[*]}")

# Write back
cat > "$PROPS" << EOF
# Auto-updated by bump-version script. Used by app/build.gradle.kts.
versionCode=$NEW_VERSION_CODE
versionName=$NEW_VERSION_NAME
EOF

echo "Version: $VERSION_NAME ($VERSION_CODE) -> $NEW_VERSION_NAME ($NEW_VERSION_CODE)"

if [ "${1:-}" = "--build" ]; then
  shift
  cd "$SCRIPT_DIR"
  ./gradlew assembleDebug "$@"
fi
