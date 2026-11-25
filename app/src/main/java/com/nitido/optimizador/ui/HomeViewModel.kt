package com.nitido.optimizador.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nitido.optimizador.core.BatteryInfo
import com.nitido.optimizador.core.CacheCleaner
import com.nitido.optimizador.core.DeviceMonitor
import com.nitido.optimizador.core.Health
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.Prefs
import com.nitido.optimizador.core.RamInfo
import com.nitido.optimizador.core.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()

    var storage by mutableStateOf<StorageInfo?>(null)
        private set
    var ram by mutableStateOf<RamInfo?>(null)
        private set
    var battery by mutableStateOf<BatteryInfo?>(null)
        private set
    var cacheBytes by mutableStateOf<Long?>(null)
        private set
    var health by mutableStateOf<Health?>(null)
        private set
    var hasUsage by mutableStateOf(true)
        private set
    var hasFiles by mutableStateOf(true)
        private set
    var lastOptimized by mutableStateOf(0L)
        private set
    var totalFreed by mutableStateOf(0L)
        private set
    var optimizations by mutableStateOf(0)
        private set
    var uptimeDays by mutableStateOf(0L)
        private set

    fun refresh() {
        val c = ctx
        viewModelScope.launch {
            val st = withContext(Dispatchers.IO) { DeviceMonitor.storage(c) }
            val r = DeviceMonitor.ram(c)
            val b = DeviceMonitor.battery(c)
            val prefs = Prefs(c)
            hasUsage = Perms.hasUsageAccess(c)
            hasFiles = Perms.hasFilesAccess(c)
            storage = st
            ram = r
            battery = b
            lastOptimized = prefs.lastOptimizedAt
            totalFreed = prefs.totalFreed
            optimizations = prefs.optimizations
            uptimeDays = DeviceMonitor.uptimeDays()
            health = DeviceMonitor.health(st, r, cacheBytes, b, lastOptimized)
            val cache = CacheCleaner(c).totalCacheBytes()
            cacheBytes = cache
            health = DeviceMonitor.health(st, r, cache, b, lastOptimized)
        }
    }
}
