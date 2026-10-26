#!/usr/bin/env bash
# Turns the errors in a Gradle log into GitHub annotations, so a failed run shows what broke on
# the pull request itself (and through the checks API) without opening the raw log.
# Usage: annotate-failures.sh <gradle log>
set -uo pipefail

log="$1"
root="${GITHUB_WORKSPACE:-$PWD}/"

# GitHub drops the message after a newline unless it is escaped.
escape() { sed -e 's/%/%25/g' -e 's/\r/%0D/g' | awk 'BEGIN{ORS="%0A"} {print}' | sed 's/%0A$//'; }

# Kotlin compiler: "e: file:///…/File.kt:12:5 message"
grep -E '^e: (file://)?/' "$log" | head -n 20 | while IFS= read -r line; do
  rest="${line#e: }"; rest="${rest#file://}"
  location="${rest%% *}"; message="${rest#* }"
  file="${location%%:*}"; numbers="${location#*:}"
  echo "::error file=${file#"$root"},line=${numbers%%:*},title=Kotlin::$message"
done

# detekt: "/…/File.kt:12:5: message [RuleId]"
grep -E '^/[^ ]+\.kt:[0-9]+:[0-9]+: ' "$log" | head -n 20 | while IFS= read -r line; do
  file="${line%%:*}"; rest="${line#*:}"; number="${rest%%:*}"; message="${rest#*: }"
  echo "::error file=${file#"$root"},line=$number,title=detekt::$message"
done

# C and C++ (clang through ninja, often indented in Gradle's output): "/…/file.cpp:12:5: error: message"
grep -oE '/[^ :]+\.(c|cc|cpp|h|hpp):[0-9]+:[0-9]+: (fatal )?error: .*' "$log" | sort -u | head -n 20 |
  while IFS= read -r line; do
    file="${line%%:*}"; rest="${line#*:}"; number="${rest%%:*}"; message="${rest#*error: }"
    echo "::error file=${file#"$root"},line=$number,title=C++::$message"
  done

# The linker, CMake and ninja's failed steps, which name no source line.
text=$(grep -E 'ld(\.lld)?: error:|CMake Error|^ *FAILED: ' -A 3 "$log" | head -n 40 | escape)
[ -n "$text" ] && echo "::error title=Native build::$text"

# Android lint's text report: "app/src/…/File.kt:12: Error: message [Id]"
report=app/build/reports/lint-results-debug.txt
if [ -f "$report" ]; then
  grep -E '^[^ ]+:[0-9]+: Error: ' "$report" | head -n 20 | while IFS= read -r line; do
    file="${line%%:*}"; rest="${line#*:}"; number="${rest%%:*}"; message="${rest#*: Error: }"
    echo "::error file=$file,line=$number,title=Lint::$message"
  done
fi

# Failed unit tests: "FooTest > bar FAILED" and the assertion under it.
text=$(grep -E ' > .* FAILED$' -A 2 "$log" | head -n 30 | escape)
[ -n "$text" ] && echo "::error title=Unit tests::$text"

# Gradle's own summary of each failed task.
text=$(grep -E -A 12 '^\* What went wrong:' "$log" | head -n 60 | escape)
[ -n "$text" ] && echo "::error title=Gradle::$text"
exit 0
