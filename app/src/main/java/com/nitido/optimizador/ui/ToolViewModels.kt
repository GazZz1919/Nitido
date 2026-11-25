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
import com.nitido.optimizador.core.BoostResult
import com.nitido.optimizador.core.CacheCleaner
import com.nitido.optimizador.core.DeviceMonitor
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.MemoryBooster
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.Prefs
import com.nitido.optimizador.core.RamInfo
import com.nitido.optimizador.core.ScanConfig
import com.nitido.optimizador.core.ScanProgress
import com.nitido.optimizador.core.StorageInfo
import com.nitido.optimizador.core.StorageScanner
import com.nitido.optimizador.core.SystemApps
import com.nitido.optimizador.core.SystemTweaks
import com.nitido.optimizador.work.MaintenanceScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================================================================
// Liberar caché
// =============================================================================================

/** Limpieza guiada: Nítido abre los ajustes de cada app, una tras otra. */
data class GuidedState(
    val queue: List<AppCache>,
    val index: Int,
    val freed: Long,
    val pendingLaunch: Boolean,
) {
    val current: AppCache? get() = queue.getOrNull(index)
}

class CacheViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    var total by mutableStateOf<Long?>(null)
        private set
    var apps by mutableStateOf<List<AppCache>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var hasUsage by mutableStateOf(false)
        private set
    var showSystem by mutableStateOf(false)
        private set
    var trimming by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var guided by mutableStateOf<GuidedState?>(null)
        private set

    private var freeBeforeSystemClear = 0L

    val visibleApps: List<AppCache>
        get() = apps.filter { it.cacheBytes > 0 && (showSystem || !it.isSystem) }

    fun updateShowSystem(value: Boolean) {
        showSystem = value
    }

    fun load() {
        hasUsage = Perms.hasUsageAccess(ctx)
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val cleaner = CacheCleaner(ctx)
                total = cleaner.totalCacheBytes()
                apps = cleaner.appCaches()
            } finally {
                loading = false
            }
        }
    }

    fun trim() {
        if (trimming) return
        trimming = true
        viewModelScope.launch {
            try {
                val freed = CacheCleaner(ctx).trimSystemCaches()
                Prefs(ctx).addFreed(freed)
                message = if (freed > 0) {
                    "Se liberaron ${Fmt.bytes(freed)} de caché."
                } else {
                    "No había caché por encima de la reserva que mantiene Android. " +
                        "Usa la limpieza guiada para las apps que más ocupan."
                }
            } finally {
                trimming = false
            }
            load()
        }
    }

    fun beforeSystemClear() {
        freeBeforeSystemClear = DeviceMonitor.freeBytes()
    }

    fun afterSystemClear() {
        if (freeBeforeSystemClear > 0L) {
            val freed = (DeviceMonitor.freeBytes() - freeBeforeSystemClear).coerceAtLeast(0L)
            Prefs(ctx).addFreed(freed)
            message = if (freed > 0) "Android liberó ${Fmt.bytes(freed)} de caché." else "No había caché externa que borrar."
        }
        freeBeforeSystemClear = 0L
        load()
    }

    fun startGuided() {
        val queue = visibleApps.filter { it.cacheBytes >= 5_000_000L }.take(15)
        if (queue.isEmpty()) {
            message = "Ninguna app tiene más de 5 MB de caché. ¡Todo en orden!"
            return
        }
        message = null
        guided = GuidedState(queue = queue, index = 0, freed = 0L, pendingLaunch = true)
    }

    fun markLaunched() {
        guided = guided?.copy(pendingLaunch = false)
    }

    /** Se llama al volver de los ajustes de una app: mide lo liberado y pasa a la siguiente. */
    fun onGuidedReturn() {
        val state = guided ?: return
        val app = state.current ?: return
        viewModelScope.launch {
            val now = CacheCleaner(ctx).cacheOf(app.packageName)
            val freedHere = if (now != null) (app.cacheBytes - now).coerceAtLeast(0L) else 0L
            val latest = guided ?: return@launch
            val updated = latest.copy(freed = latest.freed + freedHere)
            val next = updated.index + 1
            if (next >= updated.queue.size) {
                finishGuided(updated.freed)
            } else {
                guided = updated
                delay(400)
                val still = guided ?: return@launch
                guided = still.copy(index = next, pendingLaunch = true)
            }
        }
    }

    fun stopGuided() {
        val state = guided ?: return
        finishGuided(state.freed)
    }

    private fun finishGuided(freed: Long) {
        guided = null
        Prefs(ctx).addFreed(freed)
        message = if (freed > 0) {
            "Limpieza guiada terminada: se liberaron ${Fmt.bytes(freed)}."
        } else {
            "Limpieza guiada terminada."
        }
        load()
    }
}

// =============================================================================================
// Liberar espacio
// =============================================================================================

class StorageViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    val review = ReviewState()

    var storage by mutableStateOf<StorageInfo?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var progress by mutableStateOf<ScanProgress?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var largeMb by mutableStateOf(Prefs(app).largeFileMb)
        private set
    var oldDays by mutableStateOf(Prefs(app).oldDownloadDays)
        private set

    private var scanJob: Job? = null

    fun refreshStorage() {
        viewModelScope.launch {
            storage = withContext(Dispatchers.IO) { DeviceMonitor.storage(ctx) }
        }
    }

    fun updateLargeMb(value: Int) {
        largeMb = value
        Prefs(ctx).largeFileMb = value
    }

    fun updateOldDays(value: Int) {
        oldDays = value
        Prefs(ctx).oldDownloadDays = value
    }

    fun scan() {
        if (scanning) return
        scanning = true
        progress = null
        message = null
        scanJob = viewModelScope.launch {
            try {
                val config = ScanConfig(largeFileBytes = largeMb * 1_000_000L, oldDownloadDays = oldDays)
                val result = StorageScanner(ctx).scan(config) { p -> progress = p }
                review.load(result)
            } finally {
                scanning = false
                progress = null
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val res = review.deleteSelected(StorageScanner(ctx)) ?: return@launch
            Prefs(ctx).addFreed(res.freedBytes)
            message = deletionMessage(res)
            refreshStorage()
        }
    }
}

// =============================================================================================
// Liberar memoria
// =============================================================================================

class MemoryViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    var ram by mutableStateOf<RamInfo?>(null)
        private set
    var boosting by mutableStateOf(false)
        private set
    var result by mutableStateOf<BoostResult?>(null)
        private set
    var recent by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var hasUsage by mutableStateOf(false)
        private set

    val canCloseOthers: Boolean = MemoryBooster.canCloseOtherApps

    fun refreshRam() {
        ram = DeviceMonitor.ram(ctx)
    }

    fun refresh() {
        refreshRam()
        hasUsage = Perms.hasUsageAccess(ctx)
        viewModelScope.launch {
            recent = withContext(Dispatchers.IO) { AppAnalyzer(ctx).recentApps() }
        }
    }

    fun boost() {
        if (boosting) return
        boosting = true
        viewModelScope.launch {
            try {
                result = MemoryBooster(ctx).boost()
                refreshRam()
            } finally {
                boosting = false
            }
        }
    }
}

// =============================================================================================
// Estabilidad y mantenimiento
// =============================================================================================

class MaintenanceViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val prefs = Prefs(app)

    var autoEnabled by mutableStateOf(prefs.autoEnabled)
        private set
    var interval by mutableStateOf(prefs.autoIntervalHours)
        private set
    var onlyCharging by mutableStateOf(prefs.autoOnlyCharging)
        private set
    var cleanJunk by mutableStateOf(prefs.autoCleanJunk)
        private set
    var notifyResults by mutableStateOf(prefs.notifyResults)
        private set
    var lowStorage by mutableStateOf(prefs.lowStorageAlert)
        private set
    var threshold by mutableStateOf(prefs.lowStorageThreshold)
        private set
    var unusedReminder by mutableStateOf(prefs.unusedReminder)
        private set
    var animationScale by mutableStateOf(SystemTweaks.currentAnimationScale(app))
        private set
    var canTweak by mutableStateOf(Perms.hasSecureSettings(app))
        private set
    var unused by mutableStateOf<List<AppEntry>?>(null)
        private set
    var loadingUnused by mutableStateOf(false)
        private set

    private fun reschedule() = MaintenanceScheduler.apply(ctx)

    fun updateAuto(value: Boolean) {
        autoEnabled = value
        prefs.autoEnabled = value
        reschedule()
    }

    fun updateInterval(hours: Int) {
        interval = hours
        prefs.autoIntervalHours = hours
        reschedule()
    }

    fun updateOnlyCharging(value: Boolean) {
        onlyCharging = value
        prefs.autoOnlyCharging = value
        reschedule()
    }

    fun updateCleanJunk(value: Boolean) {
        cleanJunk = value
        prefs.autoCleanJunk = value
    }

    fun updateNotify(value: Boolean) {
        notifyResults = value
        prefs.notifyResults = value
    }

    fun updateLowStorage(value: Boolean) {
        lowStorage = value
        prefs.lowStorageAlert = value
        reschedule()
    }

    fun updateThreshold(value: Int) {
        threshold = value
        prefs.lowStorageThreshold = value
    }

    fun updateUnusedReminder(value: Boolean) {
        unusedReminder = value
        prefs.unusedReminder = value
        reschedule()
    }

    fun refreshTweaks() {
        canTweak = Perms.hasSecureSettings(ctx)
        animationScale = SystemTweaks.currentAnimationScale(ctx)
    }

    /** Devuelve false si Android no permitió el cambio. */
    fun updateAnimationScale(scale: Float): Boolean {
        val ok = SystemTweaks.setAnimationScale(ctx, scale)
        refreshTweaks()
        return ok
    }

    fun loadUnused() {
        if (loadingUnused) return
        loadingUnused = true
        viewModelScope.launch {
            try {
                unused = withContext(Dispatchers.IO) { AppAnalyzer(ctx).unusedApps(30) }
            } finally {
                loadingUnused = false
            }
        }
    }

    fun refreshUnused() {
        unused = unused?.filter { SystemApps.isInstalled(ctx, it.packageName) }
    }
}
