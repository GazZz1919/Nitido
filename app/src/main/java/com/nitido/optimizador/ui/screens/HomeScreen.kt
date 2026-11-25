package com.nitido.optimizador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Health
import com.nitido.optimizador.ui.Dest
import com.nitido.optimizador.ui.HomeViewModel
import com.nitido.optimizador.ui.components.IconBadge
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.MetricCard
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.PermissionRow
import com.nitido.optimizador.ui.components.ScoreGauge
import com.nitido.optimizador.ui.components.SectionTitle
import com.nitido.optimizador.ui.components.ToolRow
import com.nitido.optimizador.ui.components.rememberFilesAccessRequest
import com.nitido.optimizador.ui.components.rememberUsageAccessRequest
import com.nitido.optimizador.ui.theme.LocalAccents

@Composable
fun HomeScreen(onNavigate: (Dest) -> Unit, vm: HomeViewModel = viewModel()) {
    OnResume { vm.refresh() }
    val accents = LocalAccents.current
    val requestUsage = rememberUsageAccessRequest { vm.refresh() }
    val requestFiles = rememberFilesAccessRequest { vm.refresh() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HomeHeader() }

            item {
                HealthCard(
                    health = vm.health,
                    lastOptimized = vm.lastOptimized,
                    onQuick = { onNavigate(Dest.QUICK) },
                    onDeep = { onNavigate(Dest.DEEP) },
                )
            }

            if (!vm.hasFiles || !vm.hasUsage) {
                item {
                    NCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Lock, accents.cache)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Activa el análisis completo", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Nítido necesita estos permisos para medir y limpiar de verdad. " +
                                        "Todo se procesa en tu dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (!vm.hasFiles) {
                            PermissionRow("Acceso a archivos", "Buscar basura, duplicados y archivos grandes", requestFiles)
                        }
                        if (!vm.hasUsage) {
                            PermissionRow("Acceso a datos de uso", "Ver la caché de cada app y detectar apps sin usar") {
                                requestUsage()
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val st = vm.storage
                    MetricCard(
                        icon = Icons.Rounded.SdStorage,
                        title = "Almacenamiento",
                        value = st?.let { "${Fmt.bytes(it.freeBytes)} libres" } ?: "—",
                        caption = st?.let { "${Fmt.bytes(it.usedBytes)} de ${Fmt.bytes(it.totalBytes)}" } ?: "",
                        progress = st?.usedFraction,
                        color = accents.storage,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Dest.STORAGE) },
                    )
                    val r = vm.ram
                    MetricCard(
                        icon = Icons.Rounded.Memory,
                        title = "Memoria RAM",
                        value = r?.let { "${Fmt.bytes(it.availBytes)} libres" } ?: "—",
                        caption = r?.let { "${Fmt.bytes(it.usedBytes)} de ${Fmt.bytes(it.totalBytes)}" } ?: "",
                        progress = r?.usedFraction,
                        color = accents.memory,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Dest.MEMORY) },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val cache = vm.cacheBytes
                    MetricCard(
                        icon = Icons.Rounded.Cached,
                        title = "Caché de apps",
                        value = when {
                            cache != null -> Fmt.bytes(cache)
                            !vm.hasUsage -> "Sin permiso"
                            else -> "Midiendo…"
                        },
                        caption = if (vm.hasUsage) "acumulada por tus apps" else "concede acceso de uso",
                        progress = null,
                        color = accents.cache,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Dest.CACHE) },
                    )
                    val b = vm.battery
                    val hot = b != null && b.temperatureC >= 43f
                    MetricCard(
                        icon = if (b?.charging == true) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        title = "Batería",
                        value = if (b != null && b.level >= 0) "${b.level} %" else "—",
                        caption = b?.let {
                            "${Fmt.temperature(it.temperatureC)} · " + if (it.charging) "Cargando" else "Salud: ${it.healthText}"
                        } ?: "",
                        progress = b?.let { it.level.coerceAtLeast(0) / 100f },
                        color = if (hot) accents.danger else accents.battery,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val battery = vm.battery
            if (battery != null && battery.temperatureC >= 43f) {
                item {
                    InfoNote(
                        "El teléfono está caliente (${Fmt.temperature(battery.temperatureC)}). Con calor, Android " +
                            "reduce la velocidad del procesador: deja que se enfríe y evita usarlo mientras carga.",
                        icon = Icons.Rounded.Whatshot,
                        tint = accents.danger,
                    )
                }
            }
            if (vm.uptimeDays >= 7) {
                item {
                    InfoNote(
                        "Tu dispositivo lleva ${vm.uptimeDays} días sin reiniciarse. Reiniciarlo libera memoria, " +
                            "cierra procesos atascados y suele devolver la fluidez.",
                        icon = Icons.Rounded.PowerSettingsNew,
                        tint = accents.cache,
                    )
                }
            }

            item { SectionTitle("Herramientas") }
            item {
                ToolRow(Icons.Rounded.Cached, "Liberar caché", "Caché del sistema y de cada app", accents.cache) {
                    onNavigate(Dest.CACHE)
                }
            }
            item {
                ToolRow(Icons.Rounded.SdStorage, "Liberar espacio", "Basura, duplicados, APK y archivos grandes", accents.storage) {
                    onNavigate(Dest.STORAGE)
                }
            }
            item {
                ToolRow(Icons.Rounded.Memory, "Liberar memoria", "Estado de la RAM y apps en segundo plano", accents.memory) {
                    onNavigate(Dest.MEMORY)
                }
            }
            item {
                ToolRow(Icons.Rounded.VerifiedUser, "Estabilidad y mantenimiento", "Automatización, alertas y ajustes", accents.battery) {
                    onNavigate(Dest.MAINTENANCE)
                }
            }

            item {
                Text(
                    if (vm.optimizations > 0) {
                        "Nítido ha liberado ${Fmt.bytes(vm.totalFreed)} en ${vm.optimizations} optimizaciones"
                    } else {
                        "Haz tu primera optimización para empezar"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(primary, secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Nítido", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Optimizador del dispositivo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthCard(health: Health?, lastOptimized: Long, onQuick: () -> Unit, onDeep: () -> Unit) {
    NCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreGauge(score = health?.score ?: 0, label = health?.label ?: "Analizando…")
            Spacer(Modifier.height(4.dp))
            val issues = health?.issues.orEmpty()
            Text(
                when {
                    health == null -> ""
                    issues.isEmpty() -> "Todo en orden"
                    else -> issues.take(2).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "Última optimización: ${Fmt.ago(lastOptimized)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onQuick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.FlashOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Optimización rápida", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDeep,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.TrackChanges, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Optimización profunda", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
