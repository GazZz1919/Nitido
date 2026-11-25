package com.nitido.optimizador.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Utilidades de formato en español. */
object Fmt {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")
    private val spanish: Locale = Locale.forLanguageTag("es")

    /** Tamaño legible en unidades SI (las mismas que usa Ajustes de Android). */
    fun bytes(value: Long): String {
        var v = abs(value).toDouble()
        var i = 0
        while (v >= 1000 && i < units.lastIndex) {
            v /= 1000
            i++
        }
        val sign = if (value < 0) "-" else ""
        return when {
            i == 0 -> "$sign${v.toLong()} ${units[i]}"
            v >= 100 -> String.format(Locale.getDefault(), "%s%.0f %s", sign, v, units[i])
            else -> String.format(Locale.getDefault(), "%s%.1f %s", sign, v, units[i])
        }
    }

    fun percent(fraction: Float): String = "${(fraction * 100).toInt().coerceIn(0, 100)} %"

    fun temperature(celsius: Float): String = String.format(Locale.getDefault(), "%.1f °C", celsius)

    fun duration(ms: Long): String = String.format(Locale.getDefault(), "%.1f s", ms / 1000.0)

    fun minutes(ms: Long): String {
        val min = ms / 60_000
        return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
    }

    fun date(timestamp: Long): String =
        SimpleDateFormat("d MMM yyyy", spanish).format(Date(timestamp))

    /** "hace 5 min", "ayer", "hace 3 días"… */
    fun ago(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "nunca"
        val diff = (now - timestamp).coerceAtLeast(0L)
        val min = diff / 60_000
        val hours = min / 60
        val days = hours / 24
        return when {
            min < 1 -> "hace un momento"
            min < 60 -> "hace $min min"
            hours < 24 -> "hace $hours h"
            days == 1L -> "ayer"
            days < 30 -> "hace $days días"
            days < 365 -> {
                val months = days / 30
                if (months == 1L) "hace 1 mes" else "hace $months meses"
            }
            else -> "hace más de un año"
        }
    }
}
