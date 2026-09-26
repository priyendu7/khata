#!/usr/bin/env bash
# PRD privacy principle 2: only allowlisted libraries may reach the app. Lists every group:artifact
# on :app's release runtime classpath and fails on anything not in config/dependency-allowlist.txt.
# Usage: check-dependency-allowlist.sh   (run from the repo root; needs a working ./gradlew)
set -euo pipefail

allowlist=config/dependency-allowlist.txt
deps=$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath \
  | grep -oE '[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[^ ]+' | cut -d: -f1,2 | sort -u)
[ -n "$deps" ] || { echo "::error::No dependencies found; is the configuration name right?"; exit 1; }

patterns=$(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$allowlist" | grep -v '^$')

allowed() {
  local dep=$1 group=${1%%:*} p
  for p in $patterns; do
    case "$p" in
      *.\*) [[ "$group" == "${p%.\*}" || "$group" == "${p%\*}"* ]] && return 0 ;;
      *:*) [[ "$dep" == "$p" ]] && return 0 ;;
      *) [[ "$group" == "$p" ]] && return 0 ;;
    esac
  done
  return 1
}

status=0
count=0
while IFS= read -r dep; do
  count=$((count + 1))
  if ! allowed "$dep"; then
    echo "::error::$dep is not in $allowlist. Only allowlisted libraries may reach the app (docs/PRD.md, privacy principle 2); see CONTRIBUTING.md."
    status=1
  fi
done <<< "$deps"
[ $status -eq 0 ] && echo "OK: all $count release dependencies are allowlisted."
exit $status
