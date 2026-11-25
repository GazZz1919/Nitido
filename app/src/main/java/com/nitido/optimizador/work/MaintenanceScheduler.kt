package com.nitido.optimizador.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nitido.optimizador.core.Prefs
import java.util.concurrent.TimeUnit

/** Programa (o cancela) el trabajo periódico según las preferencias del usuario. */
object MaintenanceScheduler {

    private const val WORK_NAME = "nitido_mantenimiento"

    fun apply(context: Context) {
        val prefs = Prefs(context)
        val workManager = WorkManager.getInstance(context)
        val needed = prefs.autoEnabled || prefs.lowStorageAlert || prefs.unusedReminder
        if (!needed) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val hours = if (prefs.autoEnabled) prefs.autoIntervalHours.toLong() else 24L
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(prefs.autoEnabled && prefs.autoOnlyCharging)
            .build()
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
