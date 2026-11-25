package com.nitido.optimizador.core

import android.content.Context
import android.provider.Settings

/**
 * Ajustes avanzados del sistema. Necesitan el permiso WRITE_SECURE_SETTINGS, que Android
 * solo concede por ADB:  adb shell pm grant com.nitido.optimizador android.permission.WRITE_SECURE_SETTINGS
 */
object SystemTweaks {

    private val animationKeys = arrayOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    fun currentAnimationScale(context: Context): Float =
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }

    /** Cambia las tres escalas de animación. Devuelve false si falta el permiso. */
    fun setAnimationScale(context: Context, scale: Float): Boolean =
        try {
            for (key in animationKeys) {
                Settings.Global.putFloat(context.contentResolver, key, scale)
            }
            true
        } catch (e: SecurityException) {
            false
        }

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** Comandos ADB útiles (sin root) para una optimización completa desde un ordenador. */
    val adbCommands: List<Pair<String, String>> = listOf(
        "adb shell pm trim-caches 999G" to "Borra la caché de todas las apps de una vez",
        "adb shell cmd package bg-dexopt-job" to "Recompila las apps para que abran más rápido",
        "adb shell am kill-all" to "Cierra todos los procesos en segundo plano",
    )
}
