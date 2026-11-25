package com.nitido.optimizador.core

import android.content.Context

/** Preferencias y estadísticas de la app. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("nitido_prefs", Context.MODE_PRIVATE)

    // --- Mantenimiento automático ---
    var autoEnabled: Boolean
        get() = sp.getBoolean(K_AUTO, false)
        set(value) { sp.edit().putBoolean(K_AUTO, value).apply() }

    var autoIntervalHours: Int
        get() = sp.getInt(K_INTERVAL, 24)
        set(value) { sp.edit().putInt(K_INTERVAL, value).apply() }

    var autoOnlyCharging: Boolean
        get() = sp.getBoolean(K_CHARGING, true)
        set(value) { sp.edit().putBoolean(K_CHARGING, value).apply() }

    var autoCleanJunk: Boolean
        get() = sp.getBoolean(K_JUNK, true)
        set(value) { sp.edit().putBoolean(K_JUNK, value).apply() }

    var notifyResults: Boolean
        get() = sp.getBoolean(K_NOTIFY, true)
        set(value) { sp.edit().putBoolean(K_NOTIFY, value).apply() }

    // --- Alertas ---
    var lowStorageAlert: Boolean
        get() = sp.getBoolean(K_LOW, true)
        set(value) { sp.edit().putBoolean(K_LOW, value).apply() }

    var lowStorageThreshold: Int
        get() = sp.getInt(K_LOW_PCT, 10)
        set(value) { sp.edit().putInt(K_LOW_PCT, value).apply() }

    var unusedReminder: Boolean
        get() = sp.getBoolean(K_UNUSED, true)
        set(value) { sp.edit().putBoolean(K_UNUSED, value).apply() }

    var lastLowStorageAlert: Long
        get() = sp.getLong(K_LAST_LOW, 0L)
        set(value) { sp.edit().putLong(K_LAST_LOW, value).apply() }

    var lastUnusedReminder: Long
        get() = sp.getLong(K_LAST_UNUSED, 0L)
        set(value) { sp.edit().putLong(K_LAST_UNUSED, value).apply() }

    // --- Análisis ---
    var largeFileMb: Int
        get() = sp.getInt(K_LARGE, 100)
        set(value) { sp.edit().putInt(K_LARGE, value).apply() }

    var oldDownloadDays: Int
        get() = sp.getInt(K_OLD, 90)
        set(value) { sp.edit().putInt(K_OLD, value).apply() }

    var deepAutoClean: Boolean
        get() = sp.getBoolean(K_DEEP_AUTO, true)
        set(value) { sp.edit().putBoolean(K_DEEP_AUTO, value).apply() }

    // --- Estadísticas ---
    val lastOptimizedAt: Long get() = sp.getLong(K_LAST_OPT, 0L)
    val totalFreed: Long get() = sp.getLong(K_TOTAL, 0L)
    val optimizations: Int get() = sp.getInt(K_COUNT, 0)

    /** Registra una optimización completa (rápida, profunda o automática). */
    fun recordOptimization(freedBytes: Long) {
        sp.edit()
            .putLong(K_LAST_OPT, System.currentTimeMillis())
            .putLong(K_TOTAL, totalFreed + freedBytes.coerceAtLeast(0L))
            .putInt(K_COUNT, optimizations + 1)
            .apply()
    }

    /** Suma espacio liberado por una limpieza manual (archivos, caché…). */
    fun addFreed(freedBytes: Long) {
        if (freedBytes <= 0L) return
        sp.edit().putLong(K_TOTAL, totalFreed + freedBytes).apply()
    }

    private companion object {
        const val K_AUTO = "auto_enabled"
        const val K_INTERVAL = "auto_interval_hours"
        const val K_CHARGING = "auto_only_charging"
        const val K_JUNK = "auto_clean_junk"
        const val K_NOTIFY = "notify_results"
        const val K_LOW = "low_storage_alert"
        const val K_LOW_PCT = "low_storage_threshold"
        const val K_UNUSED = "unused_reminder"
        const val K_LAST_LOW = "last_low_storage_alert"
        const val K_LAST_UNUSED = "last_unused_reminder"
        const val K_LARGE = "large_file_mb"
        const val K_OLD = "old_download_days"
        const val K_DEEP_AUTO = "deep_auto_clean"
        const val K_LAST_OPT = "last_optimized_at"
        const val K_TOTAL = "total_freed"
        const val K_COUNT = "optimizations"
    }
}
