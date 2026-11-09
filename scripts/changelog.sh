#!/usr/bin/env bash
# Prints a release changelog for everything between the previous tag and the given one.
# Conventional-commit prefixes (`feat:`, `fix:`, …) become sections; anything else lands in
# "Other". Usage: scripts/changelog.sh v1.2.0
set -euo pipefail
cd "$(dirname "$0")/.."

tag=${1:?usage: changelog.sh <tag>}
range=$tag
previous=$(git describe --tags --abbrev=0 "${tag}^" 2>/dev/null || true)
[ -n "$previous" ] && range="$previous..$tag"

repo=$(git remote get-url origin 2>/dev/null | sed -E 's#^git@([^:]+):#https://\1/#; s#\.git$##' || true)

git log --no-merges --pretty=format:'%s%x09%h' "$range" | awk -F'\t' '
  function emit(section) {
    if (!(section in items)) return
    print "### " section
    printf "%s\n", items[section]
  }
  BEGIN {
    n = split("feat:Features fix:Fixes perf:Performance refactor:Refactoring docs:Documentation ci:CI build:Build test:Tests style:Style chore:Chores", pairs, " ")
    for (i = 1; i <= n; i++) {
      split(pairs[i], kv, ":")
      title[kv[1]] = kv[2]
      order[i] = kv[2]
    }
  }
  {
    message = $1
    type = message
    sub(/:.*/, "", type)
    sub(/\(.*/, "", type)
    section = (type in title) ? title[type] : "Other"
    sub(/^[^:]*: /, "", message)
    items[section] = items[section] "- " message " (" $2 ")\n"
  }
  END {
    print "## What changed\n"
    for (i = 1; i <= n; i++) emit(order[i])
    emit("Other")
  }
'
if [ -n "$previous" ]; then
  printf '**Full changelog**: %s/compare/%s...%s\n' "${repo:-<repo>}" "$previous" "$tag"
else
  echo "First release."
fi
