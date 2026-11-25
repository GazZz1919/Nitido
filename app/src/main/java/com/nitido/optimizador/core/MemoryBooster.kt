package com.nitido.optimizador.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class BoostResult(
    val availBefore: Long,
    val availAfter: Long,
    val appsChecked: Int,
    val supported: Boolean,
) {
    val gained: Long get() = (availAfter - availBefore).coerceAtLeast(0L)
}

/**
 * Liberación de memoria RAM.
 *
 * Hasta Android 13, ActivityManager.killBackgroundProcesses() cierra los procesos en segundo
 * plano de otras apps (los mismos que Android cerraría si necesitara memoria).
 * Desde Android 14 el sistema solo permite cerrar los procesos propios, así que allí
 * Nítido libera su propia memoria y muestra qué apps detener a mano.
 */
class MemoryBooster(private val context: Context) {

    companion object {
        val canCloseOtherApps: Boolean
            get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    suspend fun boost(): BoostResult = withContext(Dispatchers.Default) {
        val before = DeviceMonitor.ram(context).availBytes
        var checked = 0
        val am = if (canCloseOtherApps) context.getSystemService(ActivityManager::class.java) else null
        if (am != null) {
            val protectedApps = SystemApps.protectedPackages(context)
            for (info in SystemApps.installed(context)) {
                // Nunca se tocan apps del sistema, el launcher, el teclado ni la propia app.
                if (info.packageName in protectedApps || SystemApps.isSystem(info)) continue
                try {
                    am.killBackgroundProcesses(info.packageName)
                    checked++
                } catch (e: SecurityException) {
                    // Sin permiso para esta app: se ignora.
                }
            }
        }
        CacheCleaner(context).clearOwnCache()
        System.gc()
        delay(1500) // Da tiempo al sistema a recuperar la memoria antes de medir.
        val after = DeviceMonitor.ram(context).availBytes
        BoostResult(before, after, checked, canCloseOtherApps)
    }
}
