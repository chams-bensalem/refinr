#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chams Bensalem
# SPDX-License-Identifier: AGPL-3.0-only
#
# Mesure du verdict ai-ready contre eval/verdict (E0). Jamais en CI : "run" appelle le modele (quota, argent).
#   scripts/eval-verdict.sh check
#   scripts/eval-verdict.sh describe env|anthropic|anthropic-check
#   scripts/eval-verdict.sh estimate <target> <runs>
#   scripts/eval-verdict.sh run <target> <runs> [--out DIR] [--pause 15] [--cap N: 100 anthropic, 40 sinon] [--budget 20] [--phase E0]
#   scripts/eval-verdict.sh summarize <resultsDir> [--baseline DIR[,DIR]]
#   scripts/eval-verdict.sh generate <target> <spec.md>... [--out DIR]   (generation du backlog, appelle le modele)
# Reglages : backend/src/main/resources/application-example.properties (defauts de production) + .env a la racine.
set -euo pipefail
cd "$(dirname "$0")/../backend"

java_bin="${REFINR_EVAL_JAVA:-}"
if [[ -z "$java_bin" ]]; then
  if [[ -n "${JAVA_HOME:-}" ]]; then java_bin="$JAVA_HOME/bin/java"; else java_bin="$(command -v java)"; fi
  major=$("$java_bin" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
  if [[ "${major%%.*}" -lt 25 && -x /usr/libexec/java_home ]]; then
    java_bin="$(/usr/libexec/java_home -v 25)/bin/java"
  fi
fi

mvn -q -ntp test-compile
cp_file=target/eval-classpath.txt
if [[ ! -s "$cp_file" || pom.xml -nt "$cp_file" ]]; then
  mvn -q -ntp dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile="$cp_file"
fi
# Resultats relatifs a la racine du depot pour --out et summarize
args=()
for a in "$@"; do
  if [[ "$a" == eval/* ]]; then args+=("../$a"); else args+=("$a"); fi
done
# macOS : empeche la mise en veille pendant la mesure (le minuteur monotone des appels s'arreterait)
keep_awake=()
if command -v caffeinate >/dev/null 2>&1; then keep_awake=(caffeinate -i); fi
exec "${keep_awake[@]}" "$java_bin" -cp "target/test-classes:target/classes:$(cat "$cp_file")" \
  fr.heliocraft.refinr.eval.VerdictEval --root .. "${args[@]}"
