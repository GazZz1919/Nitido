package com.nitido.optimizador.ui.screens

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Intents
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.ui.CacheViewModel
import com.nitido.optimizador.ui.GuidedState
import com.nitido.optimizador.ui.components.AppCacheRow
import com.nitido.optimizador.ui.components.IconBadge
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.LoadingRow
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.PermissionCard
import com.nitido.optimizador.ui.components.ScreenScaffold
import com.nitido.optimizador.ui.components.SectionTitle
import com.nitido.optimizador.ui.components.SuccessNote
import com.nitido.optimizador.ui.components.rememberFilesAccessRequest
import com.nitido.optimizador.ui.components.rememberIntentLauncher
import com.nitido.optimizador.ui.components.rememberUsageAccessRequest
import com.nitido.optimizador.ui.components.screenPadding
import com.nitido.optimizador.ui.theme.LocalAccents

@Composable
fun CacheScreen(onBack: () -> Unit, vm: CacheViewModel = viewModel()) {
    val context = LocalContext.current
    val accents = LocalAccents.current
    OnResume { vm.load() }
    val openSettings = rememberIntentLauncher { vm.load() }
    val requestUsage = rememberUsageAccessRequest { vm.load() }
    val systemClear = rememberIntentLauncher { vm.afterSystemClear() }
    val guidedLauncher = rememberIntentLauncher { vm.onGuidedReturn() }
    val requestFiles = rememberFilesAccessRequest()

    // Limpieza guiada: abre los ajustes de la siguiente app cuando toca.
    val guided = vm.guided
    LaunchedEffect(guided?.index, guided?.pendingLaunch) {
        val state = vm.guided
        if (state != null && state.pendingLaunch) {
            vm.markLaunched()
            val app = state.current
            if (app == null || !guidedLauncher(Intents.appDetails(app.packageName))) vm.stopGuided()
        }
    }

    ScreenScaffold(title = "Liberar caché", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (guided != null) {
                item { GuidedCard(guided, onStop = vm::stopGuided) }
            }

            item {
                NCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Cached, accents.cache)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Caché de apps", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Archivos temporales que las apps vuelven a crear cuando los necesitan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val total = vm.total
                    Text(
                        when {
                            total != null -> Fmt.bytes(total)
                            vm.hasUsage -> "Midiendo…"
                            else -> "—"
                        },
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        if (vm.hasUsage) "en caché ahora mismo" else "Concede acceso de uso para medirla",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = vm::trim,
                        enabled = !vm.trimming,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (vm.trimming) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Liberando…")
                        } else {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Liberar caché del sistema", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            vm.message?.let { msg -> item { SuccessNote(msg) } }

            val clearAll = Intents.clearAllAppCaches()
            if (clearAll != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item {
                    NCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.PhoneAndroid, accents.storage)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Limpieza de Android", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Android borra la caché externa de todas las apps tras pedirte confirmación. " +
                                        "Requiere acceso a archivos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                if (Perms.hasFilesAccess(context)) {
                                    vm.beforeSystemClear()
                                    if (!systemClear(clearAll)) {
                                        vm.afterSystemClear()
                                        Toast.makeText(context, "Tu dispositivo no ofrece esta limpieza", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    requestFiles()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Abrir la limpieza del sistema")
                        }
                    }
                }
            }

            if (!vm.hasUsage) {
                item {
                    PermissionCard(
                        title = "Ver la caché de cada app",
                        text = "Con acceso a datos de uso, Nítido mide la caché de cada app y te guía para borrarla.",
                        button = "Permitir acceso de uso",
                        onClick = { requestUsage() },
                    )
                }
            } else {
                item {
                    NCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.TouchApp, accents.memory)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Limpieza guiada", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Android solo deja borrar la caché de cada app desde sus ajustes. Nítido los abre " +
                                        "uno tras otro: pulsa «Borrar caché» y vuelve atrás.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = vm::startGuided,
                            enabled = guided == null && vm.visibleApps.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Empezar con las apps que más ocupan")
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { SectionTitle("Caché por app") }
                        Text(
                            "Sistema",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = vm.showSystem, onCheckedChange = vm::updateShowSystem)
                    }
                }

                val list = vm.visibleApps
                if (vm.loading && list.isEmpty()) {
                    item { LoadingRow("Midiendo la caché de tus apps…") }
                } else if (list.isEmpty()) {
                    item { InfoNote("Ninguna app tiene caché ahora mismo.") }
                } else {
                    items(list.take(100), key = { "app:${it.packageName}" }) { app ->
                        AppCacheRow(app) { openSettings(Intents.appDetails(app.packageName)) }
                    }
                }
            }

            item {
                InfoNote(
                    "Borrar la caché es seguro: no elimina tus datos ni tus sesiones. Las apps la vuelven a " +
                        "crear con el uso, así que es normal que crezca de nuevo.",
                )
            }
        }
    }
}

@Composable
private fun GuidedCard(state: GuidedState, onStop: () -> Unit) {
    val app = state.current
    NCard {
        Text("Limpieza guiada · ${state.index + 1} de ${state.queue.size}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.queue.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        if (app != null) {
            Text(
                "${app.label}: abre «Almacenamiento» y pulsa «Borrar caché» (${Fmt.bytes(app.cacheBytes)}). " +
                    "Después vuelve atrás y Nítido pasará a la siguiente app.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.freed > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Liberado hasta ahora: ${Fmt.bytes(state.freed)}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAccents.current.good,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Terminar") }
    }
}
