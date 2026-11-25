#!/usr/bin/env bash
# Se ejecuta si algún paso de GitHub Actions falla. Si build-apk.sh ya abrió
# un issue con el error, no hace nada; si no, abre uno con el paso que falló,
# su mensaje de error, datos del entorno y el enlace al registro.
if [ -f .issue-created ]; then
  exit 0
fi
export GH_REPO="${GH_REPO:-${GITHUB_REPOSITORY:-}}"
REPO="${GITHUB_REPOSITORY:-}"
RUN="${GITHUB_RUN_NUMBER:-?}"
URL="${GITHUB_SERVER_URL:-https://github.com}/$REPO/actions/runs/${GITHUB_RUN_ID:-}"

# Consulta la API de GitHub con el token y, si no tiene permiso, sin autenticar
# (el repositorio es público).
api() {
  gh api "$1" 2>/dev/null && return 0
  curl -fsSL -H "Accept: application/vnd.github+json" "${GITHUB_API_URL:-https://api.github.com}/$1" 2>/dev/null
}

jobs_json="$(api "repos/$REPO/actions/runs/${GITHUB_RUN_ID:-0}/jobs")"
failed_steps="$(printf '%s' "$jobs_json" | jq -r '.jobs[]?.steps[]? | select(.conclusion == "failure") | "- \(.name)"' 2>/dev/null)"
job_id="$(printf '%s' "$jobs_json" | jq -r '.jobs[0].id // empty' 2>/dev/null)"
annotations=""
if [ -n "$job_id" ]; then
  annotations="$(api "repos/$REPO/check-runs/$job_id/annotations" |
    jq -r '.[]? | "- [\(.annotation_level)] \(.title // "") \(.message)"' 2>/dev/null | cut -c1-600 | head -n 40)"
fi

{
  echo "La compilación #$RUN falló antes de terminar."
  echo
  echo "Registro completo: $URL"
  echo
  echo "**Paso que falló:**"
  echo "${failed_steps:-(no disponible)}"
  echo
  echo "**Mensajes de error:**"
  echo "${annotations:-(no disponibles)}"
  if [ -s build.log ]; then
    echo
    echo '```'
    tail -n 80 build.log | cut -c1-300
    echo '```'
  fi
  echo
  echo "**Entorno:**"
  echo '```'
  echo "Imagen: ${ImageOS:-?} ${ImageVersion:-?}"
  echo "ANDROID_HOME=${ANDROID_HOME:-} ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-}"
  ls "${ANDROID_HOME:-/nonexistent}" "${ANDROID_HOME:-/nonexistent}/platforms" "${ANDROID_HOME:-/nonexistent}/build-tools" 2>&1 | head -n 40
  echo "JAVA_HOME=${JAVA_HOME:-} JAVA_HOME_17_X64=${JAVA_HOME_17_X64:-} JAVA_HOME_21_X64=${JAVA_HOME_21_X64:-}"
  java -version 2>&1 | head -n 3
  echo "gradle: $(command -v gradle || echo 'no instalado')"
  gradle --version 2>/dev/null | grep -E "^Gradle|JVM" | head -n 3
  echo '```'
} > issue.md
gh issue create --title "Error de compilación #$RUN" --body-file issue.md
