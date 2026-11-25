#!/usr/bin/env bash
# Compila Nítido y publica el APK en "Releases".
# Lo ejecuta GitHub Actions en cada cambio (.github/workflows/compilar-apk.yml).
# Prepara por su cuenta Java 17, Gradle y el SDK de Android, así el flujo de
# trabajo no depende de acciones externas. Si algo falla, abre un "issue" con el
# error para poder corregirlo.
set -uo pipefail

GRADLE_VERSION="8.9"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

export GH_REPO="${GH_REPO:-${GITHUB_REPOSITORY:-}}"
RUN="${GITHUB_RUN_NUMBER:-0}"
TAG="build-${GITHUB_RUN_ID:-$RUN}-${GITHUB_RUN_ATTEMPT:-1}"

# El flujo antiguo (build-apk.yml) no compila si ya existe el nuevo.
case "${GITHUB_WORKFLOW_REF:-}" in
  */build-apk.yml@*)
    if [ -f .github/workflows/compilar-apk.yml ]; then
      echo "Lo compila el flujo nuevo (compilar-apk.yml)."
      exit 0
    fi
    ;;
esac

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

find_sdkmanager() {
  local f
  for f in "$ANDROID_HOME"/cmdline-tools/latest/bin/sdkmanager "$ANDROID_HOME"/cmdline-tools/*/bin/sdkmanager; do
    if [ -x "$f" ]; then
      echo "$f"
      return 0
    fi
  done
  return 1
}

setup_env() {
  # Java 17, el que usa el proyecto (si no está, se usa el que haya instalado).
  local candidate
  for candidate in "${JAVA_HOME_17_X64:-}" /usr/lib/jvm/temurin-17-jdk-amd64 /usr/lib/jvm/java-17-openjdk-amd64; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
  java -version || return 1

  # SDK de Android: el que ya trae la máquina de GitHub o, si no hay, uno nuevo.
  export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
  if [ ! -d "$ANDROID_HOME/platforms" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
    echo "Instalando las herramientas del SDK de Android en $ANDROID_HOME"
    mkdir -p "$ANDROID_HOME/cmdline-tools" /tmp/cmdline-tools
    curl -fsSL --retry 3 -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" || return 1
    unzip -q -o /tmp/cmdline-tools.zip -d /tmp/cmdline-tools || return 1
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" || return 1
  fi
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  local sdkmanager
  if sdkmanager="$(find_sdkmanager)"; then
    if [ ! -f "$ANDROID_HOME/licenses/android-sdk-license" ]; then
      yes 2>/dev/null | "$sdkmanager" --licenses > /dev/null 2>&1 || true
    fi
    if [ ! -d "$ANDROID_HOME/platforms/android-35" ] || [ ! -d "$ANDROID_HOME/build-tools/34.0.0" ]; then
      "$sdkmanager" "platforms;android-35" "build-tools;34.0.0" "platform-tools" || return 1
    fi
  fi
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  echo "SDK de Android: $ANDROID_HOME"

  # Gradle 8.9, la versión que necesita el plugin de Android 8.7.
  local gradle_home="$HOME/.nitido/gradle-$GRADLE_VERSION"
  if [ ! -x "$gradle_home/bin/gradle" ]; then
    mkdir -p "$HOME/.nitido"
    curl -fsSL --retry 3 -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" || return 1
    unzip -q -o /tmp/gradle.zip -d "$HOME/.nitido" || return 1
  fi
  export PATH="$gradle_home/bin:$PATH"
  gradle --version || return 1
}

echo "::group::Preparar Java, Gradle y el SDK de Android"
setup_env > setup.log 2>&1
setup_status=$?
cat setup.log
echo "::endgroup::"
if [ "$setup_status" -ne 0 ]; then
  report "Error preparando la compilación #$RUN" setup.log "rror|ERROR|not found|denied|failed|Failed"
  exit 1
fi

echo "::group::Compilar APK de depuración"
gradle --no-daemon --console=plain assembleDebug 2>&1 | tee build.log
debug_status=$?
echo "::endgroup::"
if [ "$debug_status" -ne 0 ]; then
  report "Error de compilación #$RUN" build.log "^e: |error:|What went wrong"
  exit 1
fi

echo "::group::Compilar APK optimizado (release)"
gradle --no-daemon --console=plain assembleRelease 2>&1 | tee release.log
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
