package com.nitido.optimizador.core

/** Estado de cada paso de una optimización. */
enum class StepStatus { PENDING, RUNNING, DONE, SKIPPED }

data class Step(
    val title: String,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String = "",
)

/** Devuelve una copia de la lista con el paso [index] actualizado. */
fun List<Step>.withStep(index: Int, status: StepStatus, detail: String? = null): List<Step> =
    mapIndexed { i, step ->
        if (i == index) step.copy(status = status, detail = detail ?: step.detail) else step
    }

/** Fracción de pasos terminados (hechos u omitidos). */
fun List<Step>.progress(): Float =
    if (isEmpty()) 0f
    else count { it.status == StepStatus.DONE || it.status == StepStatus.SKIPPED }.toFloat() / size

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val freeFraction: Float get() = 1f - usedFraction
}

data class RamInfo(
    val totalBytes: Long,
    val availBytes: Long,
    val lowMemory: Boolean,
    val thresholdBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val availFraction: Float get() = 1f - usedFraction
}

data class BatteryInfo(
    val level: Int,
    val temperatureC: Float,
    val charging: Boolean,
    val healthText: String,
)

/** Puntuación de salud del dispositivo calculada con métricas reales. */
data class Health(val score: Int, val label: String, val issues: List<String>)

/** Resultado de una optimización rápida o profunda. Todas las cifras son medidas. */
data class OptimizationReport(
    val freedBytes: Long,
    val cacheFreedBytes: Long,
    val junkFreedBytes: Long,
    val junkItems: Int,
    val ramBefore: Long,
    val ramAfter: Long,
    val appsChecked: Int,
    val durationMs: Long,
    val notes: List<String>,
)
