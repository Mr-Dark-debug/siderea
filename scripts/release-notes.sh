#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one version, without its heading.
#   scripts/release-notes.sh 0.1.0
# Exits non-zero if the version has no section, so a release can't go out without notes.
set -euo pipefail

version="${1:?usage: release-notes.sh <version without leading v>}"
changelog="$(dirname "$0")/../CHANGELOG.md"

notes="$(awk -v v="$version" '
  $0 ~ "^## \\[" v "\\]" { found = 1; next }
  found && /^## \[/      { exit }
  found                  { print }
' "$changelog")"

if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then
  echo "No CHANGELOG.md section found for version $version" >&2
  exit 1
fi

printf '%s\n' "$notes"
