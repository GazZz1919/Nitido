package com.nitido.optimizador.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Intents
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.ScanProgress
import com.nitido.optimizador.core.StorageInfo
import com.nitido.optimizador.ui.StorageViewModel
import com.nitido.optimizador.ui.components.ChoiceRow
import com.nitido.optimizador.ui.components.ConfirmDeleteDialog
import com.nitido.optimizador.ui.components.DeleteBar
import com.nitido.optimizador.ui.components.IconBadge
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.PermissionCard
import com.nitido.optimizador.ui.components.ScreenScaffold
import com.nitido.optimizador.ui.components.SuccessNote
import com.nitido.optimizador.ui.components.rememberFilesAccessRequest
import com.nitido.optimizador.ui.components.rememberIntentLauncher
import com.nitido.optimizador.ui.components.reviewItems
import com.nitido.optimizador.ui.components.screenPadding
import com.nitido.optimizador.ui.theme.LocalAccents

@Composable
fun StorageScreen(onBack: () -> Unit, vm: StorageViewModel = viewModel()) {
    val context = LocalContext.current
    var hasFiles by remember { mutableStateOf(Perms.hasFilesAccess(context)) }
    OnResume {
        hasFiles = Perms.hasFilesAccess(context)
        vm.refreshStorage()
    }
    val requestFiles = rememberFilesAccessRequest { granted -> hasFiles = granted }
    val openSettings = rememberIntentLauncher { vm.refreshStorage() }
    var confirm by remember { mutableStateOf(false) }
    val review = vm.review
    val result = review.result

    ScreenScaffold(
        title = "Liberar espacio",
        onBack = onBack,
        actions = {
            if (result != null && !vm.scanning) {
                IconButton(onClick = vm::scan) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Volver a analizar")
                }
            }
        },
        bottomBar = {
            if (result != null && !result.isEmpty) {
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
            item { StorageUsageCard(vm.storage) }

            when {
                !hasFiles -> item {
                    PermissionCard(
                        title = "Permite el acceso a archivos",
                        text = "Nítido necesita leer el almacenamiento para encontrar basura, duplicados y archivos " +
                            "grandes. Nunca borra nada sin que lo marques.",
                        button = "Conceder acceso",
                        onClick = requestFiles,
                    )
                }

                vm.scanning -> item { ScanProgressCard(vm.progress, onCancel = vm::cancelScan) }

                result == null -> {
                    item {
                        NCard {
                            Text("Ajustes del análisis", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Considerar «archivo grande» a partir de",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            ChoiceRow(
                                options = listOf(50 to "50 MB", 100 to "100 MB", 250 to "250 MB", 500 to "500 MB"),
                                selected = vm.largeMb,
                                onSelect = vm::updateLargeMb,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Descargas antiguas: sin cambios desde hace",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            ChoiceRow(
                                options = listOf(30 to "30 días", 90 to "3 meses", 180 to "6 meses", 365 to "1 año"),
                                selected = vm.oldDays,
                                onSelect = vm::updateOldDays,
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = vm::scan,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Analizar almacenamiento", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                else -> {
                    vm.message?.let { msg -> item { SuccessNote(msg) } }
                    item {
                        NCard {
                            Text(
                                "Encontrado",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(Fmt.bytes(result.totalBytes), style = MaterialTheme.typography.displaySmall)
                            Text(
                                "${result.all.size} elementos · ${result.filesScanned} archivos analizados en " +
                                    Fmt.duration(result.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (result.isEmpty) {
                        item { InfoNote("¡Todo limpio! No se encontró nada que liberar.") }
                    } else {
                        item {
                            InfoNote(
                                "Lo seguro ya está marcado. Toca una categoría para ver sus archivos y revisa " +
                                    "los grandes, duplicados y descargas antes de eliminarlos.",
                            )
                        }
                        reviewItems(review)
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        if (!openSettings(Intents.storageManager())) openSettings(Intents.storageSettings())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abrir el gestor de almacenamiento de Android")
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

@Composable
private fun StorageUsageCard(info: StorageInfo?) {
    val accents = LocalAccents.current
    NCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SdStorage, accents.storage)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Almacenamiento interno", style = MaterialTheme.typography.titleMedium)
                Text(
                    info?.let { "${Fmt.bytes(it.usedBytes)} usados de ${Fmt.bytes(it.totalBytes)}" } ?: "Calculando…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(info?.let { Fmt.percent(it.usedFraction) } ?: "", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { info?.usedFraction ?: 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = accents.storage,
            trackColor = accents.storage.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            info?.let { "${Fmt.bytes(it.freeBytes)} disponibles" } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanProgressCard(progress: ScanProgress?, onCancel: () -> Unit) {
    NCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(progress?.phase?.label ?: "Preparando…", style = MaterialTheme.typography.titleMedium)
                Text(
                    progress?.let { "${it.files} archivos analizados" } ?: "Esto puede tardar un poco",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    progress?.detail.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
    }
}
