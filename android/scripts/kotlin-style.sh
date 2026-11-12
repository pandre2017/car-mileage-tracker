#!/usr/bin/env bash
set -euo pipefail

# A pinned CLI formatter keeps local edits and CI consistent without another Gradle plugin.
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
formatter="${MILEAGE_KTFMT_JAR:-${TMPDIR:-/tmp}/car-mileage-ktfmt-0.64.jar}"
expected_sha=5b3d5286fd2defcc7dc8e28c21ddf156cc6b2d8682bdcd929ce4333e7a6201f2
if [[ ! -f "$formatter" ]]; then
  curl -fsSL 'https://repo.maven.apache.org/maven2/com/facebook/ktfmt/0.64/ktfmt-0.64-with-dependencies.jar' -o "$formatter"
fi
printf '%s  %s\n' "$expected_sha" "$formatter" | shasum -a 256 -c -
java_command="${JAVA_HOME:+$JAVA_HOME/bin/}java"
options=(--kotlinlang-style)
case "${1:---check}" in
  --check) options+=(--dry-run --set-exit-if-changed) ;;
  --write) ;;
  *) printf 'Usage: %s --check|--write\n' "$0" >&2; exit 2 ;;
esac
files=()
while IFS= read -r -d '' file; do files+=("$file"); done < <(
  find "$project_root" -type d \( -name build -o -name .gradle -o -name .kotlin \) -prune -o -type f \( -name '*.kt' -o -name '*.kts' \) -print0
)
"$java_command" -jar "$formatter" "${options[@]}" "${files[@]}"
