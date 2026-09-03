#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
# CRITICAL GIT TOPOLOGY (learned the hard way, 2026-09-03):
#   faisukhan01/dummysurfers `main`  = THE GAME (subtree of ./dummy-surfers)
#     → GitHub Actions builds the APK from main and publishes the release.
#     → main MUST always be a game-shaped tree (gradle at root + .github/).
#   faisukhan01/dummysurfers `site`  = the landing page (this repo's main).
#
# The landing page and the game share ONE GitHub repo. Pushing the landing
# tree to main silently deletes the CI workflow and kills the APK pipeline
# (this exact accident broke v5.1.0's first push). ALWAYS push via this
# script — never `git push origin main` by hand from the landing repo.
#
# Usage:  ./push-game.sh <github-token>
# ════════════════════════════════════════════════════════════════════
set -euo pipefail
cd "$(dirname "$0")"

TOKEN="${1:-}"
if [ -z "$TOKEN" ]; then
  echo "usage: ./push-game.sh <github-token>"
  exit 1
fi
REMOTE="https://faisukhan01:${TOKEN}@github.com/faisukhan01/dummysurfers.git"

echo "→ splitting dummy-surfers subtree…"
git subtree split -P dummy-surfers -b game-release >/dev/null 2>&1 || true
GAME_SHA=$(git rev-parse game-release)
echo "  game tree: $GAME_SHA"

echo "→ force-pushing game tree to main (triggers APK CI)…"
git push --force "$REMOTE" game-release:main

echo "→ backing up landing page to site branch…"
git push --force "$REMOTE" main:site

echo "✅ done. CI: https://github.com/faisukhan01/dummysurfers/actions"
echo "   APK: https://github.com/faisukhan01/dummysurfers/releases/latest/download/DummySurfers.apk"
