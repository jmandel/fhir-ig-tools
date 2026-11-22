#!/bin/bash
# Import, or re-sync, the SUSHI port from its development repository into sushi/, keeping its history, then apply
# the mechanical monorepo restructuring (scripts/sushi-restructure.sh). docs/MONOREPO.md "The SUSHI module".
#
# usage: scripts/sushi-sync.sh <sushi-port-repo> [branch]      (default branch: main)
#   Run from a clean working tree, on the branch that should receive the import.
#
# 1. Clone <repo> <branch> into a temporary directory and rewrite it with git filter-repo:
#    - every path moves under sushi/;
#    - every author/committer identity becomes this repository's user.name/user.email (the port has one author;
#      the development repository used a different address);
#    - workspace paths in file contents and commit messages are replaced: the repository's parent directory
#      (the old workspace root) by "$ROOT", and $HOME by "~".
#    filter-repo is deterministic: a re-sync rewrites the commits imported before to the same ids, so the merge
#    below brings in only the new ones.
# 2. Fetch the rewritten branch as refs/sushi-import/<branch> and merge it (the first time with
#    --allow-unrelated-histories). On a re-sync, files the restructuring moved (js/ -> common) or rewrote
#    (sushi/pom.xml is this repo's) may conflict: keep this repo's side and re-apply the upstream change by hand.
# 3. Run scripts/sushi-restructure.sh (idempotent) and commit its result, if any.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:?usage: $0 <sushi-port-repo> [branch]}"; BRANCH="${2:-main}"
SRC="$(cd "$SRC" && pwd)"
command -v git-filter-repo >/dev/null || { echo "needs git-filter-repo (https://github.com/newren/git-filter-repo)" >&2; exit 2; }
cd "$ROOT"
[ -z "$(git status --porcelain)" ] || { echo "working tree not clean" >&2; exit 2; }
NAME="$(git config user.name)"; EMAIL="$(git config user.email)"
[ -n "$NAME" ] && [ -n "$EMAIL" ] || { echo "set git user.name and user.email first" >&2; exit 2; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
git clone -q --no-local --single-branch --branch "$BRANCH" "$SRC" "$TMP/repo"
HEAD_ID="$(git -C "$TMP/repo" rev-parse --short HEAD)"

# identities: every distinct author/committer -> this repo's identity
git -C "$TMP/repo" log --format='%aE%n%cE' | sort -u | while read -r e; do
  printf '%s <%s> <%s>\n' "$NAME" "$EMAIL" "$e"
done > "$TMP/mailmap"
# workspace paths (longest first)
WS="$(dirname "$SRC")"
printf '%s==>$ROOT\n%s==>~\n' "$WS" "$HOME" > "$TMP/replace"

git -C "$TMP/repo" filter-repo --quiet --force --to-subdirectory-filter sushi \
  --mailmap "$TMP/mailmap" --replace-text "$TMP/replace" --replace-message "$TMP/replace"

git fetch -q "$TMP/repo" "+$BRANCH:refs/sushi-import/$BRANCH"
if git merge-base --is-ancestor "refs/sushi-import/$BRANCH" HEAD 2>/dev/null; then
  echo "already up to date with $BRANCH@$HEAD_ID"
elif git merge-base HEAD "refs/sushi-import/$BRANCH" >/dev/null 2>&1; then
  git merge --no-ff -m "sushi: sync with the SUSHI port's $BRANCH ($HEAD_ID)" "refs/sushi-import/$BRANCH"
else
  git merge --no-ff --allow-unrelated-histories \
    -m "sushi: import the SUSHI port ($BRANCH $HEAD_ID) into sushi/, with its history" "refs/sushi-import/$BRANCH"
fi

"$ROOT/scripts/sushi-restructure.sh"
if [ -n "$(git status --porcelain)" ]; then
  git add -A sushi common
  git commit -q -m "sushi: restructure after sync (scripts/sushi-restructure.sh)"
  echo "committed the restructuring"
fi
