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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.BoostResult
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Intents
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.RamInfo
import com.nitido.optimizador.ui.MemoryViewModel
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.PermissionCard
import com.nitido.optimizador.ui.components.RecentAppRow
import com.nitido.optimizador.ui.components.RingGauge
import com.nitido.optimizador.ui.components.ScreenScaffold
import com.nitido.optimizador.ui.components.SectionTitle
import com.nitido.optimizador.ui.components.SuccessNote
import com.nitido.optimizador.ui.components.rememberIntentLauncher
import com.nitido.optimizador.ui.components.rememberUsageAccessRequest
import com.nitido.optimizador.ui.components.screenPadding
import com.nitido.optimizador.ui.theme.LocalAccents
import kotlinx.coroutines.delay

@Composable
fun MemoryScreen(onBack: () -> Unit, vm: MemoryViewModel = viewModel()) {
    OnResume { vm.refresh() }
    // Medidor en vivo mientras la pantalla está abierta.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            vm.refreshRam()
        }
    }
    val openSettings = rememberIntentLauncher { vm.refresh() }
    val requestUsage = rememberUsageAccessRequest { vm.refresh() }

    ScreenScaffold(title = "Liberar memoria", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RamCard(vm.ram, vm.boosting, onBoost = vm::boost) }

            vm.result?.let { r -> item { BoostResultNote(r) } }

            item {
                InfoNote(
                    if (vm.canCloseOthers) {
                        "Nítido cierra los procesos en segundo plano de tus apps (nunca los del sistema, el teclado " +
                            "ni el launcher). Android puede volver a abrirlos cuando los necesite, así que el efecto es temporal."
                    } else {
                        "Desde Android 14 el sistema no permite que una app cierre a otras. Si una app va lenta o " +
                            "gasta batería, usa «Detener» en la lista de abajo para forzar su cierre."
                    },
                )
            }
            item {
                InfoNote(
                    "Tener la RAM ocupada no es malo: Android la usa para abrir tus apps más rápido y la libera " +
                        "solo cuando hace falta. Si el móvil va lento de forma constante, reinícialo.",
                    icon = Icons.Rounded.EmojiObjects,
                )
            }

            item { SectionTitle("Apps usadas en las últimas 24 horas") }
            if (!vm.hasUsage) {
                item {
                    PermissionCard(
                        title = "Ver las apps en uso",
                        text = "Con acceso a datos de uso puedes ver qué apps has usado y forzar su cierre.",
                        button = "Permitir acceso de uso",
                        onClick = { requestUsage() },
                    )
                }
            } else if (vm.recent.isEmpty()) {
                item { InfoNote("No hay actividad reciente registrada.") }
            } else {
                items(vm.recent, key = { "recent:${it.packageName}" }) { app ->
                    RecentAppRow(app) { openSettings(Intents.appDetails(app.packageName)) }
                }
            }
        }
    }
}

@Composable
private fun RamCard(ram: RamInfo?, boosting: Boolean, onBoost: () -> Unit) {
    val accents = LocalAccents.current
    NCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingGauge(fraction = ram?.usedFraction ?: 0f, color = accents.memory, diameter = 180.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ram?.let { Fmt.percent(it.usedFraction) } ?: "—", style = MaterialTheme.typography.displaySmall)
                    Text("en uso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Total", ram?.let { Fmt.bytes(it.totalBytes) } ?: "—")
                Stat("En uso", ram?.let { Fmt.bytes(it.usedBytes) } ?: "—")
                Stat("Disponible", ram?.let { Fmt.bytes(it.availBytes) } ?: "—")
            }
            if (ram?.lowMemory == true) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android indica que la memoria está baja",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.danger,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBoost,
                enabled = !boosting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (boosting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Liberando…")
                } else {
                    Icon(Icons.Rounded.Memory, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Liberar memoria", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoostResultNote(result: BoostResult) {
    val text = buildString {
        append("RAM disponible: ${Fmt.bytes(result.availBefore)} → ${Fmt.bytes(result.availAfter)}")
        if (result.gained > 0) append(" (+${Fmt.bytes(result.gained)})")
        append(". ")
        if (result.supported) {
            append("Se cerraron los procesos en segundo plano de tus apps (${result.appsChecked} revisadas).")
        } else {
            append("Se liberó la memoria de Nítido; Android gestiona el resto.")
        }
        if (result.gained == 0L) append(" Android ya estaba aprovechando bien la memoria.")
    }
    SuccessNote(text)
}
