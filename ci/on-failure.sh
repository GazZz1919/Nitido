#!/usr/bin/env bash
# Se ejecuta si algún paso de GitHub Actions falla. Si build-apk.sh ya abrió
# un issue con el error, no hace nada; si no, abre uno con el enlace al registro.
if [ -f .issue-created ]; then
  exit 0
fi
export GH_REPO="${GH_REPO:-${GITHUB_REPOSITORY:-}}"
RUN="${GITHUB_RUN_NUMBER:-?}"
URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
{
  echo "La compilación #$RUN falló antes de terminar."
  echo
  echo "Registro completo: $URL"
  if [ -s build.log ]; then
    echo
    echo '```'
    tail -n 80 build.log | cut -c1-300
    echo '```'
  fi
} > issue.md
gh issue create --title "Error de compilación #$RUN" --body-file issue.md
