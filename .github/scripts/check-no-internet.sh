#!/usr/bin/env bash
# PRD privacy principle 1: android.permission.INTERNET must never appear in a merged manifest.
# Checks every merged manifest Gradle produced (run after assembling/bundling any variant), so a
# dependency that adds the permission fails the build instead of shipping.
# Usage: check-no-internet.sh [build-dir]   (default: app/build)
set -euo pipefail

build_dir="${1:-app/build}"
manifests=$(find "$build_dir/intermediates" -path '*merged_manifest*' -name AndroidManifest.xml 2>/dev/null || true)

if [ -z "$manifests" ]; then
  echo "::error::No merged manifests under $build_dir/intermediates. Build a variant first (e.g. ./gradlew assembleDebug)."
  exit 1
fi

status=0
while IFS= read -r m; do
  if grep -q 'android.permission.INTERNET"' "$m"; then
    echo "::error file=$m::android.permission.INTERNET is in the merged manifest. BahiKhata must not have internet access (docs/PRD.md, privacy principle 1). Find the dependency that adds it in app/build/outputs/logs/manifest-merger-*-report.txt and remove it."
    status=1
  else
    echo "OK (no INTERNET): $m"
  fi
done <<< "$manifests"
exit $status
