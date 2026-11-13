#!/bin/sh
# SPDX-FileCopyrightText: 2026 Chams Bensalem
# SPDX-License-Identifier: AGPL-3.0-only
#
# Fails if a source file has no "SPDX-License-Identifier: AGPL-3.0-only" line in its first 5 lines.
#   scripts/check-spdx.sh            all tracked files (CI)
#   scripts/check-spdx.sh --staged   files added or modified in the index (pre-commit hook)
# Run from anywhere inside the repository.

set -eu
cd "$(git rev-parse --show-toplevel)"

# Source files: code, build and container files of the backend and frontend, Compose and GitHub configuration,
# .env.example, git hooks and scripts. Not covered: JSON (no comment syntax), Markdown, images, lock files, ignore files.
SOURCES='^backend/.*\.(java|xml|properties)$|^frontend/.*\.(ts|tsx|js|mjs|cjs|css|html)$|(^|/)Dockerfile$|^frontend/docker/|^\.githooks/|^scripts/.*\.sh$|^docker-compose\.ya?ml$|^\.github/.*\.ya?ml$|^\.env\.example$'
EXPECTED='SPDX-License-Identifier: AGPL-3.0-only'

if [ "${1:-}" = "--staged" ]; then
  files=$(git diff --cached --name-only --diff-filter=ACMR)
else
  files=$(git ls-files)
fi

missing=0
checked=0
# One path per line (names with spaces or glob characters stay intact); a here-document keeps the counters
# in the current shell.
while IFS= read -r f; do
  [ -n "$f" ] || continue
  # --staged: check the content that will be committed (index), even if the file was removed from the disk
  if [ "${1:-}" = "--staged" ]; then
    head5=$(git show ":$f" | head -n 5)
  else
    [ -f "$f" ] || continue
    head5=$(head -n 5 "$f")
  fi
  checked=$((checked + 1))
  if ! printf '%s\n' "$head5" | grep -qF "$EXPECTED"; then
    echo "missing SPDX header: $f" >&2
    missing=$((missing + 1))
  fi
done <<EOF_FILES
$(printf '%s\n' "$files" | grep -E "$SOURCES" || true)
EOF_FILES

if [ "$missing" -gt 0 ]; then
  cat >&2 <<EOF

$missing source file(s) without the license header. Add these two lines at the top of each file
(after a shebang, a Dockerfile "# syntax=" directive, "<?xml ...?>" or "<!doctype html>"),
using the file's comment syntax (//, #, /* */ or <!-- -->):

  SPDX-FileCopyrightText: <year> <copyright holder>
  $EXPECTED
EOF
  exit 1
fi
echo "SPDX headers: $checked source file(s) checked, all carry '$EXPECTED'."
