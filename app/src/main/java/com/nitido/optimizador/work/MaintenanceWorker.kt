package com.nitido.optimizador.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nitido.optimizador.core.AppAnalyzer
import com.nitido.optimizador.core.CacheCleaner
import com.nitido.optimizador.core.DeviceMonitor
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.Prefs
import com.nitido.optimizador.core.StorageScanner
import kotlinx.coroutines.CancellationException

/**
 * Trabajo periódico que mantiene el dispositivo estable:
 *  - libera caché y basura segura (si el usuario activó la optimización automática),
 *  - avisa cuando queda poco espacio,
 *  - recuerda (como mucho una vez por semana) las apps que no se usan.
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        try {
            if (prefs.autoEnabled) {
                val before = DeviceMonitor.freeBytes()
                CacheCleaner(ctx).trimSystemCaches()
                var junkItems = 0
                var junkBytes = 0L
                if (prefs.autoCleanJunk && Perms.hasFilesAccess(ctx)) {
                    val scanner = StorageScanner(ctx)
                    val result = scanner.delete(scanner.quickJunk())
                    junkItems = result.deleted
                    junkBytes = result.freedBytes
                }
                val freed = maxOf(DeviceMonitor.freeBytes() - before, junkBytes).coerceAtLeast(0L)
                prefs.recordOptimization(freed)
                if (prefs.notifyResults && (freed > 0L || junkItems > 0)) {
                    val extra = if (junkItems > 0) " y se eliminaron $junkItems archivos basura." else "."
                    Notifier.maintenanceDone(ctx, "Se liberaron ${Fmt.bytes(freed)}$extra")
                }
            }

            if (prefs.lowStorageAlert && now - prefs.lastLowStorageAlert > DAY_MS) {
                val storage = DeviceMonitor.storage(ctx)
                if (storage.freeFraction * 100f < prefs.lowStorageThreshold) {
                    Notifier.lowStorage(
                        ctx,
                        "Solo quedan ${Fmt.bytes(storage.freeBytes)} libres (${Fmt.percent(storage.freeFraction)}). " +
                            "Abre Nítido para liberar espacio.",
                    )
                    prefs.lastLowStorageAlert = now
                }
            }

            if (prefs.unusedReminder && Perms.hasUsageAccess(ctx) && now - prefs.lastUnusedReminder > 7 * DAY_MS) {
                val unused = AppAnalyzer(ctx).unusedApps(30)
                if (unused.isNotEmpty()) {
                    val size = unused.sumOf { it.sizeBytes }
                    Notifier.unusedApps(
                        ctx,
                        "Tienes ${unused.size} apps sin abrir en 30 días que ocupan ${Fmt.bytes(size)}.",
                    )
                    prefs.lastUnusedReminder = now
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        return Result.success()
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
