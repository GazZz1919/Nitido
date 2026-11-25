package com.nitido.optimizador.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nitido.optimizador.core.AppAnalyzer
import com.nitido.optimizador.core.AppCache
import com.nitido.optimizador.core.AppEntry
import com.nitido.optimizador.core.CacheCleaner
import com.nitido.optimizador.core.DeleteResult
import com.nitido.optimizador.core.DeviceMonitor
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.MemoryBooster
import com.nitido.optimizador.core.OptimizationReport
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.Prefs
import com.nitido.optimizador.core.ScanConfig
import com.nitido.optimizador.core.ScanPhase
import com.nitido.optimizador.core.ScanResult
import com.nitido.optimizador.core.Step
import com.nitido.optimizador.core.StepStatus
import com.nitido.optimizador.core.StorageScanner
import com.nitido.optimizador.core.SystemApps
import com.nitido.optimizador.core.progress
import com.nitido.optimizador.core.withStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STEP_PAUSE_MS = 350L
private const val RESULTS_TTL_MS = 30 * 60_000L

private const val NOTE_ANDROID_14 =
    "Desde Android 14 ninguna app puede cerrar a otras: el sistema gestiona la RAM automáticamente."

private const val NOTE_CACHE_RESERVE =
    "Android conserva una reserva de caché para que las apps abran rápido y solo libera la que la " +
        "supera. Para borrar la caché de apps concretas usa «Liberar caché» → limpieza guiada."

internal fun deletionMessage(res: DeleteResult): String {
    val base = "Se liberaron ${Fmt.bytes(res.freedBytes)} (${res.deleted} elementos eliminados)"
    return if (res.failed > 0) "$base. ${res.failed} no se pudieron eliminar." else "$base."
}

// =============================================================================================
// Optimización rápida
// =============================================================================================

class QuickViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    var steps by mutableStateOf(quickSteps())
        private set
    var running by mutableStateOf(false)
        private set
    var report by mutableStateOf<OptimizationReport?>(null)
        private set

    val progress: Float get() = steps.progress()

    /** Empieza una optimización nueva (si no hay una en marcha). */
    fun startFresh() {
        if (running) return
        report = null
        steps = quickSteps()
        running = true
        viewModelScope.launch {
            try {
                run()
            } finally {
                running = false
            }
        }
    }

    private fun setStep(index: Int, status: StepStatus, detail: String? = null) {
        steps = steps.withStep(index, status, detail)
    }

    private suspend fun run() {
        val c = ctx
        val started = System.currentTimeMillis()
        val notes = mutableListOf<String>()

        setStep(0, StepStatus.RUNNING)
        val freeBefore = DeviceMonitor.freeBytes()
        val ramBefore = DeviceMonitor.ram(c).availBytes
        delay(STEP_PAUSE_MS)
        setStep(0, StepStatus.DONE, "${Fmt.bytes(freeBefore)} libres · ${Fmt.bytes(ramBefore)} de RAM disponible")

        setStep(1, StepStatus.RUNNING)
        val cacheFreed = CacheCleaner(c).trimSystemCaches()
        if (cacheFreed > 0) {
            setStep(1, StepStatus.DONE, "${Fmt.bytes(cacheFreed)} liberados")
        } else {
            setStep(1, StepStatus.DONE, "Sin caché por encima de la reserva de Android")
            notes += NOTE_CACHE_RESERVE
        }

        setStep(2, StepStatus.RUNNING)
        var junkBytes = 0L
        var junkItems = 0
        if (Perms.hasFilesAccess(c)) {
            val scanner = StorageScanner(c)
            val res = scanner.delete(scanner.quickJunk())
            junkBytes = res.freedBytes
            junkItems = res.deleted
            setStep(
                2, StepStatus.DONE,
                if (junkItems > 0) "$junkItems elementos · ${Fmt.bytes(junkBytes)}" else "No había archivos basura",
            )
        } else {
            setStep(2, StepStatus.SKIPPED, "Requiere acceso a archivos")
            notes += "Concede acceso a archivos para eliminar temporales, miniaturas y carpetas vacías."
        }

        setStep(3, StepStatus.RUNNING)
        val boost = MemoryBooster(c).boost()
        if (boost.supported) {
            setStep(3, StepStatus.DONE, "Procesos en segundo plano cerrados (${boost.appsChecked} apps revisadas)")
        } else {
            setStep(3, StepStatus.DONE, "Memoria de Nítido liberada")
            notes += NOTE_ANDROID_14
        }

        setStep(4, StepStatus.RUNNING)
        val freed = maxOf(DeviceMonitor.freeBytes() - freeBefore, junkBytes).coerceAtLeast(0L)
        Prefs(c).recordOptimization(freed)
        delay(STEP_PAUSE_MS)
        val elapsed = System.currentTimeMillis() - started
        setStep(4, StepStatus.DONE, "Completado en ${Fmt.duration(elapsed)}")

        report = OptimizationReport(
            freedBytes = freed,
            cacheFreedBytes = cacheFreed,
            junkFreedBytes = junkBytes,
            junkItems = junkItems,
            ramBefore = ramBefore,
            ramAfter = boost.availAfter,
            appsChecked = boost.appsChecked,
            durationMs = elapsed,
            notes = notes,
        )
    }

    private fun quickSteps() = listOf(
        Step("Analizar el estado del dispositivo"),
        Step("Liberar caché del sistema"),
        Step("Eliminar temporales, miniaturas y carpetas vacías"),
        Step("Liberar memoria RAM"),
        Step("Generar informe"),
    )
}

// =============================================================================================
// Optimización profunda
// =============================================================================================

enum class DeepPhase { INTRO, RUNNING, RESULTS }

class DeepViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    var phase by mutableStateOf(DeepPhase.INTRO)
        private set
    var steps by mutableStateOf(deepSteps())
        private set
    var liveDetail by mutableStateOf("")
        private set
    var autoClean by mutableStateOf(Prefs(app).deepAutoClean)
        private set
    var report by mutableStateOf<OptimizationReport?>(null)
        private set
    var unused by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var topCache by mutableStateOf<List<AppCache>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val review = ReviewState()
    private var freeBeforeSystemClear = 0L
    private var finishedAt = 0L

    val progress: Float get() = steps.progress()

    /**
     * Al entrar en la pantalla: si hay un análisis en curso o uno reciente (menos de 30 min),
     * se muestra; si no, se vuelve a la introducción.
     */
    fun onEnter() {
        if (phase == DeepPhase.RESULTS && System.currentTimeMillis() - finishedAt > RESULTS_TTL_MS) {
            phase = DeepPhase.INTRO
        }
    }

    fun updateAutoClean(value: Boolean) {
        autoClean = value
        Prefs(ctx).deepAutoClean = value
    }

    fun start() {
        if (phase == DeepPhase.RUNNING) return
        phase = DeepPhase.RUNNING
        steps = deepSteps()
        liveDetail = ""
        report = null
        message = null
        unused = emptyList()
        topCache = emptyList()
        review.clear()
        viewModelScope.launch {
            try {
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "La optimización se detuvo antes de terminar: ${e.message ?: e.javaClass.simpleName}"
                finishedAt = System.currentTimeMillis()
                phase = DeepPhase.RESULTS
            }
        }
    }

    private fun setStep(index: Int, status: StepStatus, detail: String? = null) {
        steps = steps.withStep(index, status, detail)
    }

    private suspend fun run() {
        val c = ctx
        val prefs = Prefs(c)
        val started = System.currentTimeMillis()
        val notes = mutableListOf<String>()
        val hasFiles = Perms.hasFilesAccess(c)
        val hasUsage = Perms.hasUsageAccess(c)

        // 0. Estado inicial
        setStep(0, StepStatus.RUNNING)
        val freeBefore = DeviceMonitor.freeBytes()
        val ramBefore = DeviceMonitor.ram(c).availBytes
        delay(STEP_PAUSE_MS)
        setStep(0, StepStatus.DONE, "${Fmt.bytes(freeBefore)} libres · ${Fmt.bytes(ramBefore)} de RAM disponible")

        // 1-2. Almacenamiento completo y duplicados
        var scan: ScanResult? = null
        if (hasFiles) {
            setStep(1, StepStatus.RUNNING)
            val config = ScanConfig(
                largeFileBytes = prefs.largeFileMb * 1_000_000L,
                oldDownloadDays = prefs.oldDownloadDays,
            )
            val result = StorageScanner(c).scan(config) { p ->
                if (p.phase == ScanPhase.DUPLICATES && steps[1].status == StepStatus.RUNNING) {
                    setStep(1, StepStatus.DONE, "${p.files} archivos analizados")
                    setStep(2, StepStatus.RUNNING)
                }
                liveDetail = p.detail
            }
            if (steps[1].status == StepStatus.RUNNING) {
                setStep(1, StepStatus.DONE, "${result.filesScanned} archivos analizados")
            }
            val groups = result.duplicateGroups()
            setStep(2, StepStatus.DONE, if (groups == 0) "No hay duplicados" else "$groups grupos de archivos duplicados")
            scan = result
        } else {
            setStep(1, StepStatus.SKIPPED, "Requiere acceso a archivos")
            setStep(2, StepStatus.SKIPPED, "Requiere acceso a archivos")
            notes += "Concede acceso a archivos para analizar basura, duplicados y archivos grandes."
        }
        liveDetail = ""

        // 3. Apps: caché por app y apps sin usar
        if (hasUsage) {
            setStep(3, StepStatus.RUNNING)
            val caches = CacheCleaner(c).appCaches()
            val unusedApps = withContext(Dispatchers.IO) { AppAnalyzer(c).unusedApps(30) }
            topCache = caches.filter { it.cacheBytes > 0 }.take(8)
            unused = unusedApps
            setStep(3, StepStatus.DONE, "${caches.size} apps analizadas · ${unusedApps.size} sin usar")
        } else {
            setStep(3, StepStatus.SKIPPED, "Requiere acceso a datos de uso")
            notes += "Concede acceso a datos de uso para ver la caché de cada app y detectar las que no usas."
        }

        // 4. Caché del sistema
        setStep(4, StepStatus.RUNNING)
        val cacheFreed = CacheCleaner(c).trimSystemCaches()
        if (cacheFreed > 0) {
            setStep(4, StepStatus.DONE, "${Fmt.bytes(cacheFreed)} liberados")
        } else {
            setStep(4, StepStatus.DONE, "Sin caché por encima de la reserva de Android")
            notes += NOTE_CACHE_RESERVE
        }

        // 5. Basura segura (temporales, miniaturas, carpetas vacías)
        var junkBytes = 0L
        var junkItems = 0
        val scanned = scan
        if (scanned != null && autoClean) {
            setStep(5, StepStatus.RUNNING)
            val safe = scanned.all.filter { it.category.isSafeJunk && it.preselect }
            val res = StorageScanner(c).delete(safe)
            junkBytes = res.freedBytes
            junkItems = res.deleted
            scan = scanned.without(res.deletedPaths)
            setStep(
                5, StepStatus.DONE,
                if (junkItems > 0) "$junkItems elementos · ${Fmt.bytes(junkBytes)}" else "No había archivos basura",
            )
        } else if (scanned != null) {
            setStep(5, StepStatus.SKIPPED, "Desactivado: los tienes marcados abajo para revisar")
        } else {
            setStep(5, StepStatus.SKIPPED, "Requiere acceso a archivos")
        }

        // 6. Memoria RAM
        setStep(6, StepStatus.RUNNING)
        val boost = MemoryBooster(c).boost()
        if (boost.supported) {
            setStep(6, StepStatus.DONE, "Procesos en segundo plano cerrados (${boost.appsChecked} apps revisadas)")
        } else {
            setStep(6, StepStatus.DONE, "Memoria de Nítido liberada")
            notes += NOTE_ANDROID_14
        }

        // 7. Informe
        setStep(7, StepStatus.RUNNING)
        val freed = maxOf(DeviceMonitor.freeBytes() - freeBefore, junkBytes).coerceAtLeast(0L)
        prefs.recordOptimization(freed)
        scan?.let { review.load(it) }
        val elapsed = System.currentTimeMillis() - started
        report = OptimizationReport(
            freedBytes = freed,
            cacheFreedBytes = cacheFreed,
            junkFreedBytes = junkBytes,
            junkItems = junkItems,
            ramBefore = ramBefore,
            ramAfter = boost.availAfter,
            appsChecked = boost.appsChecked,
            durationMs = elapsed,
            notes = notes,
        )
        delay(STEP_PAUSE_MS)
        setStep(7, StepStatus.DONE, "Completado en ${Fmt.duration(elapsed)}")
        finishedAt = System.currentTimeMillis()
        phase = DeepPhase.RESULTS
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val res = review.deleteSelected(StorageScanner(ctx)) ?: return@launch
            Prefs(ctx).addFreed(res.freedBytes)
            message = deletionMessage(res)
        }
    }

    /** Al volver del diálogo de desinstalación, quita de la lista las apps ya desinstaladas. */
    fun refreshAfterReturn() {
        unused = unused.filter { SystemApps.isInstalled(ctx, it.packageName) }
    }

    fun beforeSystemClear() {
        freeBeforeSystemClear = DeviceMonitor.freeBytes()
    }

    fun afterSystemClear() {
        if (freeBeforeSystemClear <= 0L) return
        val freed = (DeviceMonitor.freeBytes() - freeBeforeSystemClear).coerceAtLeast(0L)
        freeBeforeSystemClear = 0L
        Prefs(ctx).addFreed(freed)
        message = if (freed > 0) "Android liberó ${Fmt.bytes(freed)} de caché." else "No había caché externa que borrar."
    }

    private fun deepSteps() = listOf(
        Step("Analizar el estado del dispositivo"),
        Step("Escanear todo el almacenamiento"),
        Step("Buscar archivos duplicados"),
        Step("Analizar apps y caché"),
        Step("Liberar caché del sistema"),
        Step("Eliminar archivos basura seguros"),
        Step("Optimizar la memoria RAM"),
        Step("Generar informe"),
    )
}
