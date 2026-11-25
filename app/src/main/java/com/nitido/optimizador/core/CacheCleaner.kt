package com.nitido.optimizador.core

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppCache(
    val packageName: String,
    val label: String,
    val cacheBytes: Long,
    val totalBytes: Long,
    val isSystem: Boolean,
)

/**
 * Liberación de caché usando solo mecanismos oficiales de Android:
 *  1. StorageManager.allocateBytes(): el sistema borra caché de otras apps para "hacer sitio"
 *     (solo la que supera la reserva mínima que Android mantiene).
 *  2. ACTION_CLEAR_APP_CACHE (Android 11+): diálogo del sistema que borra la caché externa.
 *  3. Limpieza guiada: abre los ajustes de cada app para pulsar "Borrar caché".
 */
class CacheCleaner(private val context: Context) {

    private val stats: StorageStatsManager?
        get() = context.getSystemService(StorageStatsManager::class.java)

    /** Caché total de todas las apps (requiere acceso a datos de uso). */
    suspend fun totalCacheBytes(): Long? = withContext(Dispatchers.IO) {
        if (!Perms.hasUsageAccess(context)) return@withContext null
        try {
            stats?.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())?.cacheBytes
        } catch (e: Exception) {
            null
        }
    }

    /** Caché de cada app, de mayor a menor. */
    suspend fun appCaches(): List<AppCache> = withContext(Dispatchers.IO) {
        if (!Perms.hasUsageAccess(context)) return@withContext emptyList()
        val user = Process.myUserHandle()
        val ssm = stats ?: return@withContext emptyList()
        SystemApps.installed(context).mapNotNull { info ->
            try {
                val st = ssm.queryStatsForPackage(info.storageUuid, info.packageName, user)
                AppCache(
                    packageName = info.packageName,
                    label = SystemApps.label(context, info),
                    cacheBytes = st.cacheBytes,
                    totalBytes = st.appBytes + st.dataBytes,
                    isSystem = SystemApps.isSystem(info),
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.cacheBytes }
    }

    /** Caché actual de una sola app (para medir la limpieza guiada). */
    @Suppress("DEPRECATION")
    suspend fun cacheOf(packageName: String): Long? = withContext(Dispatchers.IO) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            stats?.queryStatsForPackage(info.storageUuid, packageName, Process.myUserHandle())?.cacheBytes
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pide al sistema que libere caché de las apps (Android 8+) y borra la caché de Nítido.
     * Devuelve los bytes que realmente quedaron libres (medidos antes y después).
     */
    suspend fun trimSystemCaches(): Long = withContext(Dispatchers.IO) {
        val before = DeviceMonitor.freeBytes()
        try {
            val sm = context.getSystemService(StorageManager::class.java)
            if (sm != null) {
                val uuid = StorageManager.UUID_DEFAULT
                val allocatable = sm.getAllocatableBytes(uuid)
                if (allocatable > 0) sm.allocateBytes(uuid, allocatable)
            }
        } catch (e: Exception) {
            // Es normal: Android libera toda la caché que puede y avisa de que no llegó al objetivo.
        }
        clearOwnCache()
        (DeviceMonitor.freeBytes() - before).coerceAtLeast(0L)
    }

    /** Borra la caché de la propia app. */
    fun clearOwnCache(): Long {
        var freed = 0L
        val dirs = listOfNotNull(context.cacheDir) + context.externalCacheDirs.filterNotNull()
        for (dir in dirs) {
            dir.listFiles()?.forEach { freed += deleteRecursively(it) }
        }
        return freed
    }

    private fun deleteRecursively(file: File): Long {
        var size = 0L
        if (file.isDirectory) file.listFiles()?.forEach { size += deleteRecursively(it) }
        val length = if (file.isFile) file.length() else 0L
        if (file.delete()) size += length
        return size
    }
}
