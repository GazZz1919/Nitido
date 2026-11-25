#!/usr/bin/env bash
# Compila Nítido y publica el APK en "Releases".
# Lo ejecuta GitHub Actions (.github/workflows/build-apk.yml) en cada cambio.
# Si la compilación falla, abre un "issue" con el error para poder corregirlo.
set -uo pipefail

export GH_REPO="${GH_REPO:-${GITHUB_REPOSITORY:-}}"
RUN="${GITHUB_RUN_NUMBER:-0}"
TAG="build-$RUN"
if [ "${GITHUB_RUN_ATTEMPT:-1}" != "1" ]; then
  TAG="$TAG.${GITHUB_RUN_ATTEMPT}"
fi

# Abre un issue con las líneas importantes de un registro.
report() {
  local title="$1" log="$2" pattern="$3"
  touch "$log"
  {
    echo "$title"
    echo
    echo '```'
    grep -E "$pattern" -A4 "$log" | cut -c1-300 | head -n 150 || true
    echo '--- final del registro ---'
    tail -n 80 "$log" | cut -c1-300
    echo '```'
  } > issue.md
  gh issue create --title "$title" --body-file issue.md && touch .issue-created
}

echo "::group::Compilar APK de depuración"
gradle --no-daemon assembleDebug 2>&1 | tee build.log
debug_status=$?
echo "::endgroup::"
if [ "$debug_status" -ne 0 ]; then
  report "Error de compilación #$RUN" build.log "^e: |error:|What went wrong"
  exit 1
fi

echo "::group::Compilar APK optimizado (release)"
gradle --no-daemon assembleRelease 2>&1 | tee release.log
release_status=$?
echo "::endgroup::"

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/Nitido-debug.apk
if [ "$release_status" -eq 0 ] && [ -f app/build/outputs/apk/release/app-release.apk ]; then
  cp app/build/outputs/apk/release/app-release.apk dist/Nitido.apk
fi

if ! gh release create "$TAG" dist/*.apk \
  --target "${GITHUB_SHA:-main}" \
  --title "Nítido · compilación $RUN" \
  --notes "Instala Nitido.apk (si no aparece, usa Nitido-debug.apk)." 2> publish.log; then
  report "No se pudo publicar el APK (#$RUN)" publish.log "."
  exit 1
fi

if [ "$release_status" -ne 0 ]; then
  report "Aviso: la versión release no compiló (#$RUN)" release.log "^e: |ERROR|What went wrong|Missing class"
fi
exit 0
