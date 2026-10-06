#!/usr/bin/env bash
# Manage git worktrees so multiple agents/sessions can work on TyMed in parallel
# without stepping on each other's edits, build output, or Gradle daemons.
#
# Each worktree gets its own directory under ../TyMed-worktrees/, its own branch,
# and its own copies of the untracked files Android builds need (local.properties,
# keystore.properties, keystores/) since `git worktree` only shares tracked files.
#
# Usage:
#   scripts/worktree.sh new <branch> [base-branch]   # create a worktree + branch
#   scripts/worktree.sh rm <branch>                  # remove a worktree + its dir
#   scripts/worktree.sh list                         # list all worktrees
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKTREES_DIR="$(cd "$REPO_ROOT/.." && pwd)/TyMed-worktrees"

sanitize() { echo "$1" | tr '/' '-'; }

cmd_new() {
  local branch="${1:?usage: worktree.sh new <branch> [base-branch]}"
  local base="${2:-main}"
  local dir="$WORKTREES_DIR/$(sanitize "$branch")"

  if [ -d "$dir" ]; then
    echo "error: $dir already exists" >&2
    exit 1
  fi

  mkdir -p "$WORKTREES_DIR"
  git -C "$REPO_ROOT" fetch origin "$base"

  if git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$REPO_ROOT" worktree add "$dir" "$branch"
  else
    git -C "$REPO_ROOT" worktree add -b "$branch" "$dir" "origin/$base"
  fi

  # Copy untracked files Android needs that git worktree won't bring along.
  for f in android/local.properties keystore.properties; do
    if [ -f "$REPO_ROOT/$f" ]; then
      cp "$REPO_ROOT/$f" "$dir/$f"
    fi
  done
  if [ -d "$REPO_ROOT/keystores" ]; then
    cp -R "$REPO_ROOT/keystores" "$dir/keystores"
  fi

  echo "Worktree ready: $dir (branch $branch, based on origin/$base)"
}

cmd_rm() {
  local branch="${1:?usage: worktree.sh rm <branch>}"
  shift
  local dir="$WORKTREES_DIR/$(sanitize "$branch")"
  git -C "$REPO_ROOT" worktree remove "$dir" "$@"
  echo "Removed worktree: $dir"
}

cmd_list() {
  git -C "$REPO_ROOT" worktree list
}

case "${1:-}" in
  new) shift; cmd_new "$@" ;;
  rm) shift; cmd_rm "$@" ;;
  list) cmd_list ;;
  *)
    echo "usage: $0 {new <branch> [base]|rm <branch>|list}" >&2
    exit 1
    ;;
esac
