package com.nitido.optimizador.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Intents
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.StepStatus
import com.nitido.optimizador.ui.DeepPhase
import com.nitido.optimizador.ui.DeepViewModel
import com.nitido.optimizador.ui.components.AppCacheRow
import com.nitido.optimizador.ui.components.Bullet
import com.nitido.optimizador.ui.components.ConfirmDeleteDialog
import com.nitido.optimizador.ui.components.DeleteBar
import com.nitido.optimizador.ui.components.IconBadge
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.PermissionRow
import com.nitido.optimizador.ui.components.ResultHero
import com.nitido.optimizador.ui.components.RunningHero
import com.nitido.optimizador.ui.components.ScreenScaffold
import com.nitido.optimizador.ui.components.SectionTitle
import com.nitido.optimizador.ui.components.StepRow
import com.nitido.optimizador.ui.components.SuccessNote
import com.nitido.optimizador.ui.components.SwitchRow
import com.nitido.optimizador.ui.components.UnusedAppRow
import com.nitido.optimizador.ui.components.rememberFilesAccessRequest
import com.nitido.optimizador.ui.components.rememberIntentLauncher
import com.nitido.optimizador.ui.components.rememberUsageAccessRequest
import com.nitido.optimizador.ui.components.reviewItems
import com.nitido.optimizador.ui.components.screenPadding

@Composable
fun DeepScreen(onBack: () -> Unit, vm: DeepViewModel = viewModel()) {
    val context = LocalContext.current

    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!entered) {
            entered = true
            vm.onEnter()
        }
    }

    var hasFiles by remember { mutableStateOf(Perms.hasFilesAccess(context)) }
    var hasUsage by remember { mutableStateOf(Perms.hasUsageAccess(context)) }
    OnResume {
        hasFiles = Perms.hasFilesAccess(context)
        hasUsage = Perms.hasUsageAccess(context)
    }
    val requestFiles = rememberFilesAccessRequest { granted -> hasFiles = granted }
    val openSettings = rememberIntentLauncher {
        hasUsage = Perms.hasUsageAccess(context)
        vm.refreshAfterReturn()
    }
    val systemClear = rememberIntentLauncher { vm.afterSystemClear() }
    val requestUsage = rememberUsageAccessRequest { hasUsage = Perms.hasUsageAccess(context) }
    var confirm by remember { mutableStateOf(false) }
    val review = vm.review
    val phase = vm.phase

    ScreenScaffold(
        title = "Optimización profunda",
        onBack = onBack,
        bottomBar = {
            val result = review.result
            if (phase == DeepPhase.RESULTS && result != null && !result.isEmpty) {
                DeleteBar(
                    count = review.selectedItems.size,
                    bytes = review.selectedBytes,
                    deleting = review.deleting,
                    progress = review.deleteProgress,
                    onDelete = { confirm = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (phase) {
                DeepPhase.INTRO -> {
                    item {
                        NCard {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                IconBadge(Icons.Rounded.TrackChanges, MaterialTheme.colorScheme.primary, 72.dp)
                                Spacer(Modifier.height(12.dp))
                                Text("Optimización profunda", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Analiza todo el almacenamiento, tus apps y la memoria. Limpia lo que es seguro " +
                                        "y te deja revisar el resto antes de borrar nada.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Bullet("Escaneo completo: temporales, miniaturas, carpetas vacías, APK, duplicados, archivos grandes y descargas antiguas")
                            Bullet("Caché del sistema y caché de cada app")
                            Bullet("Apps que no abres desde hace 30 días")
                            Bullet("Memoria RAM y procesos en segundo plano")
                            Spacer(Modifier.height(8.dp))
                            SwitchRow(
                                title = "Eliminar basura segura automáticamente",
                                subtitle = "Temporales, miniaturas y carpetas vacías. Lo demás siempre lo revisas tú.",
                                checked = vm.autoClean,
                                onChange = vm::updateAutoClean,
                            )
                        }
                    }
                    if (!hasFiles || !hasUsage) {
                        item {
                            NCard {
                                Text("Permisos recomendados", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Sin ellos el análisis será parcial. Todo se procesa en tu dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                if (!hasFiles) {
                                    PermissionRow("Acceso a archivos", "Para analizar y limpiar el almacenamiento", requestFiles)
                                }
                                if (!hasUsage) {
                                    PermissionRow("Acceso a datos de uso", "Para la caché de cada app y las apps sin usar") {
                                        requestUsage()
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = vm::start,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.TrackChanges, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Iniciar optimización profunda", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                DeepPhase.RUNNING -> {
                    item {
                        val current = vm.steps.firstOrNull { it.status == StepStatus.RUNNING }
                        RunningHero(
                            title = "Optimizando a fondo…",
                            subtitle = vm.liveDetail.ifEmpty { current?.title ?: "Preparando…" },
                            progress = vm.progress,
                            icon = Icons.Rounded.TrackChanges,
                        )
                    }
                    item { NCard { vm.steps.forEach { StepRow(it) } } }
                    item {
                        InfoNote("Puedes volver al inicio: el análisis sigue en marcha y verás el resultado al regresar.")
                    }
                }

                DeepPhase.RESULTS -> {
                    val report = vm.report
                    if (report != null) {
                        item {
                            ResultHero(
                                value = Fmt.bytes(report.freedBytes),
                                title = "liberados automáticamente",
                                subtitle = "RAM disponible: ${Fmt.bytes(report.ramBefore)} → ${Fmt.bytes(report.ramAfter)}" +
                                    " · ${Fmt.duration(report.durationMs)}",
                            )
                        }
                    }
                    vm.message?.let { msg -> item { SuccessNote(msg) } }
                    item { NCard { vm.steps.forEach { StepRow(it) } } }
                    if (report != null && report.notes.isNotEmpty()) {
                        item { InfoNote(report.notes.distinct().joinToString("\n\n")) }
                    }

                    val result = review.result
                    if (result != null) {
                        item { SectionTitle("Revisa y libera más espacio") }
                        if (result.isEmpty) {
                            item { InfoNote("No quedan archivos por revisar: tu almacenamiento está limpio.") }
                        } else {
                            item {
                                InfoNote(
                                    "Ya están marcados los instaladores de apps que tienes instaladas. Revisa los " +
                                        "archivos grandes, duplicados y descargas antes de eliminarlos.",
                                )
                            }
                            reviewItems(review)
                        }
                    }

                    if (vm.unused.isNotEmpty()) {
                        item { SectionTitle("Apps sin usar en 30 días (${vm.unused.size})") }
                        items(vm.unused, key = { "unused:${it.packageName}" }) { app ->
                            UnusedAppRow(app) { openSettings(Intents.uninstall(app.packageName)) }
                        }
                    }

                    if (vm.topCache.isNotEmpty()) {
                        item { SectionTitle("Apps con más caché") }
                        items(vm.topCache, key = { "cache:${it.packageName}" }) { app ->
                            AppCacheRow(app) { openSettings(Intents.appDetails(app.packageName)) }
                        }
                        val clearAll = Intents.clearAllAppCaches()
                        if (clearAll != null) {
                            item {
                                FilledTonalButton(
                                    onClick = {
                                        if (Perms.hasFilesAccess(context)) {
                                            vm.beforeSystemClear()
                                            if (!systemClear(clearAll)) vm.afterSystemClear()
                                        } else {
                                            requestFiles()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Borrar la caché externa de todas las apps")
                                }
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = vm::start,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text("Repetir la optimización profunda")
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDeleteDialog(
            count = review.selectedItems.size,
            bytes = review.selectedBytes,
            onConfirm = {
                confirm = false
                vm.deleteSelected()
            },
            onDismiss = { confirm = false },
        )
    }
}
