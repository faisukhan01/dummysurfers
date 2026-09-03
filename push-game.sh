#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
# PUSH FLOW v2 (2026-09-04) — the subtree-split era is OVER.
#
#   faisukhan01/dummysurfers `main`  = THE GAME — now a REAL standalone
#     git clone at ./dummy-surfers (own .git, own origin). CI builds the
#     APK from main and publishes the release.
#   faisukhan01/dummysurfers `site`  = the landing page (this repo's main).
#
# WHY: the old flow subtree-split a landing-shaped tree. A rebuild of the
# inner repo silently produced a tree whose ROOT was the landing page (game
# under dummy-surfers/) — GitHub Actions only reads .github/workflows at the
# REPO ROOT, so CI died silently (zero runs, zero APKs). The fix: the game
# is pushed from its own clone whose root IS the game tree.
#
# Usage:  ./push-game.sh [message]   (or push inside dummy-surfers directly)
# ════════════════════════════════════════════════════════════════════
set -euo pipefail
cd "$(dirname "$0")/dummy-surfers"

echo "→ pushing game (root-shaped tree) to origin main…"
git push origin HEAD:main

echo "→ backing up landing page to site branch…"
cd ..
git push origin main:site

echo "✅ done. CI: https://github.com/faisukhan01/dummysurfers/actions"
echo "   APK: https://github.com/faisukhan01/dummysurfers/releases/latest/download/DummySurfers.apk"
