package com.nitido.optimizador.core

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings

/** Accesos directos a pantallas del sistema. */
object Intents {

    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    /** Abre el diálogo de desinstalación del sistema (el usuario confirma). */
    fun uninstall(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    /** Gestor de almacenamiento del sistema (Files de Google u otro según el fabricante). */
    fun storageManager(): Intent = Intent(StorageManager.ACTION_MANAGE_STORAGE)

    fun storageSettings(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    fun batteryUsage(): Intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)

    fun batteryOptimization(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun developerOptions(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    fun displaySettings(): Intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)

    /**
     * Android 11+: diálogo oficial del sistema que borra la caché externa de todas las apps.
     * Requiere el permiso "Acceso a todos los archivos".
     */
    fun clearAllAppCaches(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(StorageManager.ACTION_CLEAR_APP_CACHE) else null
}
