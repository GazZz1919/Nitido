package com.nitido.optimizador.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.StepStatus
import com.nitido.optimizador.ui.QuickViewModel
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.PermissionCard
import com.nitido.optimizador.ui.components.ResultHero
import com.nitido.optimizador.ui.components.RunningHero
import com.nitido.optimizador.ui.components.ScreenScaffold
import com.nitido.optimizador.ui.components.StepRow
import com.nitido.optimizador.ui.components.rememberFilesAccessRequest
import com.nitido.optimizador.ui.components.screenPadding

@Composable
fun QuickScreen(onBack: () -> Unit, onDeep: () -> Unit, vm: QuickViewModel = viewModel()) {
    val context = LocalContext.current

    // Una optimización nueva por cada visita (rememberSaveable evita repetirla al girar la pantalla).
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            vm.startFresh()
        }
    }

    var hasFiles by remember { mutableStateOf(Perms.hasFilesAccess(context)) }
    OnResume { hasFiles = Perms.hasFilesAccess(context) }
    val requestFiles = rememberFilesAccessRequest { granted -> hasFiles = granted }

    val report = vm.report
    ScreenScaffold(title = "Optimización rápida", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                if (report == null) {
                    val current = vm.steps.firstOrNull { it.status == StepStatus.RUNNING }?.title ?: "Preparando…"
                    RunningHero(
                        title = "Optimizando…",
                        subtitle = current,
                        progress = vm.progress,
                        icon = Icons.Rounded.FlashOn,
                    )
                } else {
                    ResultHero(
                        value = Fmt.bytes(report.freedBytes),
                        title = "de espacio liberado",
                        subtitle = "RAM disponible: ${Fmt.bytes(report.ramBefore)} → ${Fmt.bytes(report.ramAfter)}",
                    )
                }
            }

            item {
                NCard {
                    vm.steps.forEach { StepRow(it) }
                }
            }

            if (report != null) {
                if (report.notes.isNotEmpty()) {
                    item { InfoNote(report.notes.joinToString("\n\n")) }
                }
                if (!hasFiles) {
                    item {
                        PermissionCard(
                            title = "Limpia también los archivos basura",
                            text = "Con acceso a archivos, la optimización rápida elimina temporales, miniaturas y carpetas vacías.",
                            button = "Conceder acceso a archivos",
                            onClick = requestFiles,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Listo")
                        }
                        OutlinedButton(onClick = onDeep, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Hacer una optimización profunda")
                        }
                    }
                }
            }
        }
    }
}
