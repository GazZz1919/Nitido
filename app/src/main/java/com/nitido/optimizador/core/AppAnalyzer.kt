package com.nitido.optimizador.core

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process

data class AppEntry(
    val packageName: String,
    val label: String,
    /** Último uso conocido (0 si Android no tiene registro). */
    val lastUsed: Long,
    val foregroundMs: Long,
    /** App + datos + caché. 0 si no se pudo medir. */
    val sizeBytes: Long,
)

/** Análisis de apps con las estadísticas de uso de Android (requiere acceso a datos de uso). */
class AppAnalyzer(private val context: Context) {

    private val pm = context.packageManager

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }

    /** Apps instaladas por el usuario que se pueden abrir desde el launcher. */
    private fun userApps(): List<ApplicationInfo> {
        val protectedApps = SystemApps.protectedPackages(context)
        return SystemApps.installed(context).filter { info ->
            info.packageName !in protectedApps &&
                !SystemApps.isSystem(info) &&
                pm.getLaunchIntentForPackage(info.packageName) != null
        }
    }

    /** Apps que no se han abierto en los últimos [days] días (y no se instalaron hace poco). */
    fun unusedApps(days: Int = 30): List<AppEntry> {
        if (!Perms.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val cutoff = now - days * DAY_MS
        val recent: Map<String, UsageStats> = try {
            usm.queryAndAggregateUsageStats(cutoff, now)
        } catch (e: Exception) {
            return emptyList()
        }
        // Sin datos de uso no se puede saber qué apps no se usan: mejor no adivinar.
        if (recent.isEmpty()) return emptyList()
        val history: Map<String, UsageStats> = try {
            usm.queryAndAggregateUsageStats(now - 365 * DAY_MS, now)
        } catch (e: Exception) {
            emptyMap()
        }

        return userApps().mapNotNull { info ->
            val pkg = info.packageName
            val r = recent[pkg]
            if (r != null && r.totalTimeInForeground > 0) return@mapNotNull null
            val installed = SystemApps.firstInstallTime(context, pkg)
            if (installed == 0L || installed > cutoff) return@mapNotNull null
            val h = history[pkg]
            val last = if (h != null && h.totalTimeInForeground > 0) h.lastTimeUsed else 0L
            if (last > cutoff) return@mapNotNull null
            AppEntry(
                packageName = pkg,
                label = SystemApps.label(context, info),
                lastUsed = last,
                foregroundMs = h?.totalTimeInForeground ?: 0L,
                sizeBytes = appSize(info),
            )
        }.sortedByDescending { it.sizeBytes }
    }

    /** Apps usadas en las últimas [hours] horas, de la más reciente a la más antigua. */
    fun recentApps(hours: Int = 24, limit: Int = 15): List<AppEntry> {
        if (!Perms.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats: Map<String, UsageStats> = try {
            usm.queryAndAggregateUsageStats(now - hours * HOUR_MS, now)
        } catch (e: Exception) {
            return emptyList()
        }
        val apps = userApps().associateBy { it.packageName }
        return stats.values
            .filter { it.totalTimeInForeground > 0 && it.packageName in apps }
            .sortedByDescending { it.lastTimeUsed }
            .take(limit)
            .map { s ->
                AppEntry(
                    packageName = s.packageName,
                    label = SystemApps.label(context, apps.getValue(s.packageName)),
                    lastUsed = s.lastTimeUsed,
                    foregroundMs = s.totalTimeInForeground,
                    sizeBytes = 0L,
                )
            }
    }

    private fun appSize(info: ApplicationInfo): Long =
        try {
            val st = context.getSystemService(StorageStatsManager::class.java)
                ?.queryStatsForPackage(info.storageUuid, info.packageName, Process.myUserHandle())
            if (st != null) st.appBytes + st.dataBytes else 0L
        } catch (e: Exception) {
            0L
        }
}
