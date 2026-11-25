# Nítido · Optimizador para Android

App nativa de Android (Kotlin + Jetpack Compose) para mantener el teléfono rápido, limpio y estable.
Funciona en **Android 8.0 o superior** y solo usa mecanismos reales del sistema: cada cifra que muestra
(espacio liberado, RAM, caché) se mide antes y después. No hay animaciones de "limpieza" falsas.

## Funciones

| Función | Qué hace |
|---|---|
| **Liberación de caché** | Pide a Android que libere la caché que supera su reserva (`StorageManager.allocateBytes`). En Android 11+ abre la limpieza oficial del sistema (`ACTION_CLEAR_APP_CACHE`). Muestra la caché de cada app y ofrece una **limpieza guiada** que abre los ajustes de cada app, una tras otra, y mide lo liberado. |
| **Liberación de espacio** | Escanea el almacenamiento y agrupa: temporales y registros, miniaturas, carpetas vacías, instaladores APK (marca los ya instalados), **duplicados** (comparación por hash MD5 completo), archivos grandes y descargas antiguas. Tú revisas y confirmas antes de borrar. |
| **Optimización rápida** | Un toque, unos segundos: caché del sistema, temporales, miniaturas, carpetas vacías antiguas y memoria RAM. Informe con lo liberado. |
| **Optimización profunda** | Análisis completo del almacenamiento, duplicados, caché por app, apps sin usar en 30 días, caché del sistema, basura segura y RAM. Al final te deja revisar lo que no es seguro borrar sin mirar. |
| **Liberación de memoria** | Medidor de RAM en vivo. Hasta Android 13 cierra los procesos en segundo plano de tus apps (nunca del sistema, teclado ni launcher). En Android 14+ muestra las apps usadas y acceso directo a «Forzar detención». |
| **Estabilidad y mantenimiento** | Optimización automática programada (WorkManager), aviso de poco espacio, recordatorio de apps sin usar, control de animaciones, accesos a ajustes clave del sistema, consejos y comandos ADB avanzados. |

## Cómo obtener el APK

### Opción A: con GitHub, sin instalar nada
1. Sube **todo** el contenido de esta carpeta a un repositorio de GitHub, incluida la carpeta oculta `.github`.
2. GitHub compila la app automáticamente con cada cambio (pestaña **Actions → Compilar APK**, unos 5–8 minutos).
3. Al terminar, abre la sección **Releases** del repositorio y toca **Nitido.apk** para descargarlo directamente
   en el teléfono (si no aparece, usa **Nitido-debug.apk**). Android te pedirá permitir la instalación de apps
   de origen desconocido.
4. Si la compilación falla, GitHub abre un **issue** con el error para poder corregirlo.

### Opción B: con Android Studio
1. Abre esta carpeta en Android Studio (Ladybug 2024.2 o posterior).
2. Espera a que termine la sincronización de Gradle.
3. Conecta el teléfono con la depuración USB activada y pulsa **Run**, o usa **Build → Build APK(s)**.

Desde la terminal también funciona con Gradle 8.9 instalado: `gradle assembleRelease`
(el APK queda en `app/build/outputs/apk/release/`). Si quieres usar `./gradlew`, genera antes el wrapper
con `gradle wrapper`.

## Permisos y para qué sirven

| Permiso | Uso | Cómo se concede |
|---|---|---|
| Acceso a todos los archivos | Analizar y limpiar el almacenamiento | La app abre el ajuste del sistema |
| Acceso a datos de uso | Caché de cada app, apps sin usar y apps recientes | La app abre el ajuste del sistema |
| Notificaciones | Resultado del mantenimiento y alertas | Diálogo del sistema (Android 13+) |
| Cerrar procesos en segundo plano | Liberar RAM (solo tiene efecto hasta Android 13) | Automático |
| Solicitar desinstalación | Desinstalar apps sin usar (siempre con confirmación) | Automático |
| `WRITE_SECURE_SETTINGS` (opcional) | Cambiar la velocidad de las animaciones | Una vez, por ADB |

Todo se procesa en el dispositivo; la app no usa internet.

## Lo que Android permite (y lo que no)

- **Caché:** ninguna app puede borrar la caché interna de otra app concreta sin root. Por eso Nítido usa la
  liberación del sistema, la limpieza oficial de Android 11+ y la limpieza guiada. Android conserva una reserva
  de caché para que las apps abran rápido, así que es normal que a veces la liberación del sistema dé 0 B.
- **RAM:** desde Android 14 el sistema solo deja a cada app cerrar sus propios procesos. Además, tener la RAM
  ocupada no es malo: Android la usa para abrir las apps más rápido.
- **Ajustes avanzados:** para las animaciones hay que autorizar la app una vez desde un ordenador:
  ```
  adb shell pm grant com.nitido.optimizador android.permission.WRITE_SECURE_SETTINGS
  ```

Comandos ADB útiles (sin root), también disponibles en la app para copiarlos:
```
adb shell pm trim-caches 999G          # borra la caché de todas las apps
adb shell cmd package bg-dexopt-job    # recompila las apps para que abran más rápido
adb shell am kill-all                  # cierra todos los procesos en segundo plano
```

## Estructura del proyecto

```
app/src/main/java/com/nitido/optimizador/
├── core/      Motor: escáner de almacenamiento, caché, memoria, apps, métricas, permisos, preferencias
├── work/      Mantenimiento automático (WorkManager) y notificaciones
└── ui/        Interfaz en Jetpack Compose: tema, componentes, pantallas y ViewModels
```

Versiones: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, Compose BOM 2024.12.01,
`compileSdk`/`targetSdk` 35, `minSdk` 26.

## Antes de publicar en Google Play

- Firma la versión release con tu propia clave (ahora usa la de depuración para poder instalarla directamente).
- Google Play pide justificar `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES` y `PACKAGE_USAGE_STATS`, y exige
  que la descripción no prometa mejoras de rendimiento que la app no pueda demostrar.
- Sube `targetSdk` a la versión que exija Google Play en ese momento.
