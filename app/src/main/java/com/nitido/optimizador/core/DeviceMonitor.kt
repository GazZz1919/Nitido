package com.nitido.optimizador.core

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** Lectura de métricas reales del dispositivo: almacenamiento, RAM, batería y salud. */
object DeviceMonitor {

    private const val DAY_MS = 86_400_000L

    /** Espacio realmente libre ahora mismo en la partición de datos. */
    fun freeBytes(): Long =
        try {
            StatFs(Environment.getDataDirectory().path).availableBytes
        } catch (e: Exception) {
            0L
        }

    fun storage(context: Context): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes
        val total = try {
            // Tamaño "comercial" (64 GB, 128 GB…), igual que el que muestra Ajustes.
            context.getSystemService(StorageStatsManager::class.java)
                ?.getTotalBytes(StorageManager.UUID_DEFAULT) ?: stat.totalBytes
        } catch (e: Exception) {
            stat.totalBytes
        }
        return StorageInfo(total.coerceAtLeast(free), free)
    }

    fun ram(context: Context): RamInfo {
        val am = context.getSystemService(ActivityManager::class.java) ?: return RamInfo(0L, 0L, false, 0L)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamInfo(info.totalMem, info.availMem, info.lowMemory, info.threshold)
    }

    fun battery(context: Context): BatteryInfo? {
        val intent = try {
            ContextCompat.registerReceiver(
                context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            null
        } ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Agotada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensión"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
            else -> "Desconocida"
        }
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else -1
        return BatteryInfo(percent, temperature, charging, health)
    }

    fun uptimeMs(): Long = SystemClock.elapsedRealtime()

    fun uptimeDays(): Long = uptimeMs() / DAY_MS

    /**
     * Puntuación de 0 a 100 a partir de métricas reales:
     * espacio libre (35), RAM disponible (25), caché acumulada (15),
     * temperatura (10) y mantenimiento reciente (15).
     */
    fun health(
        storage: StorageInfo,
        ram: RamInfo,
        cacheBytes: Long?,
        battery: BatteryInfo?,
        lastOptimizedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Health {
        var score = 0f
        val issues = mutableListOf<String>()

        val free = storage.freeFraction
        score += 35f * (free / 0.25f).coerceIn(0f, 1f)
        when {
            free < 0.10f -> issues += "Almacenamiento casi lleno"
            free < 0.20f -> issues += "Poco espacio libre"
        }

        score += 25f * (ram.availFraction / 0.30f).coerceIn(0f, 1f)
        if (ram.lowMemory) issues += "Memoria RAM baja"

        if (cacheBytes != null) {
            val gb = cacheBytes / 1e9f
            score += 15f * (1f - ((gb - 0.5f) / 2.5f).coerceIn(0f, 1f))
            if (gb >= 1.5f) issues += "Mucha caché acumulada"
        } else {
            score += 10f
        }

        if (battery != null && battery.temperatureC > 0f) {
            score += when {
                battery.temperatureC < 38f -> 10f
                battery.temperatureC < 43f -> 5f
                else -> 0f
            }
            if (battery.temperatureC >= 43f) issues += "Temperatura elevada"
        } else {
            score += 10f
        }

        val days = if (lastOptimizedAt > 0) (now - lastOptimizedAt) / DAY_MS.toFloat() else Float.MAX_VALUE
        score += when {
            days < 1f -> 15f
            days < 3f -> 11f
            days < 7f -> 7f
            lastOptimizedAt > 0 -> 3f
            else -> 0f
        }
        if (days >= 7f) issues += "Sin mantenimiento reciente"

        val upDays = uptimeDays()
        if (upDays >= 7) {
            score -= 5f
            issues += "$upDays días sin reiniciar"
        }

        val value = score.roundToInt().coerceIn(0, 100)
        val label = when {
            value >= 85 -> "Excelente"
            value >= 70 -> "Bueno"
            value >= 50 -> "Mejorable"
            else -> "Necesita atención"
        }
        return Health(value, label, issues)
    }
}
