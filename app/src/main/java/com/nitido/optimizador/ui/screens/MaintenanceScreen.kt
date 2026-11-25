package com.nitido.optimizador.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nitido.optimizador.core.DeviceMonitor
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.Intents
import com.nitido.optimizador.core.Perms
import com.nitido.optimizador.core.SystemTweaks
import com.nitido.optimizador.ui.MaintenanceViewModel
import com.nitido.optimizador.ui.components.Bullet
import com.nitido.optimizador.ui.components.ChoiceRow
import com.nitido.optimizador.ui.components.CommandRow
import com.nitido.optimizador.ui.components.IconBadge
import com.nitido.optimizador.ui.components.InfoNote
import com.nitido.optimizador.ui.components.LoadingRow
import com.nitido.optimizador.ui.components.NCard
import com.nitido.optimizador.ui.components.OnResume
import com.nitido.optimizador.ui.components.ScreenScaffold
import com.nitido.optimizador.ui.components.SectionTitle
import com.nitido.optimizador.ui.components.ShortcutRow
import com.nitido.optimizador.ui.components.SwitchRow
import com.nitido.optimizador.ui.components.UnusedAppRow
import com.nitido.optimizador.ui.components.rememberFilesAccessRequest
import com.nitido.optimizador.ui.components.rememberIntentLauncher
import com.nitido.optimizador.ui.components.rememberUsageAccessRequest
import com.nitido.optimizador.ui.components.screenPadding
import com.nitido.optimizador.ui.theme.LocalAccents

@Composable
fun MaintenanceScreen(onBack: () -> Unit, vm: MaintenanceViewModel = viewModel()) {
    val context = LocalContext.current
    val accents = LocalAccents.current
    val clipboard = LocalClipboardManager.current

    var hasNotifications by remember { mutableStateOf(Perms.hasNotifications(context)) }
    var hasUsage by remember { mutableStateOf(Perms.hasUsageAccess(context)) }
    var hasFiles by remember { mutableStateOf(Perms.hasFilesAccess(context)) }
    OnResume {
        hasNotifications = Perms.hasNotifications(context)
        hasUsage = Perms.hasUsageAccess(context)
        hasFiles = Perms.hasFilesAccess(context)
        vm.refreshTweaks()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifications = granted
    }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val openSettings = rememberIntentLauncher {
        hasUsage = Perms.hasUsageAccess(context)
        vm.refreshUnused()
    }
    val requestUsage = rememberUsageAccessRequest { hasUsage = Perms.hasUsageAccess(context) }
    val requestFiles = rememberFilesAccessRequest { granted ->
        hasFiles = granted
        if (granted) vm.updateCleanJunk(true)
    }
    val copy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "Comando copiado", Toast.LENGTH_SHORT).show()
    }
    val tryOpen: (Intent) -> Unit = { intent ->
        if (!openSettings(intent)) {
            Toast.makeText(context, "No disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    ScreenScaffold(title = "Estabilidad y mantenimiento", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = screenPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- Mantenimiento automático ----------------
            item { SectionTitle("Mantenimiento automático") }
            item {
                NCard {
                    CardHeader(Icons.Rounded.Autorenew, accents.storage, "Optimización programada",
                        "Libera caché y basura segura de forma periódica, sin abrir la app.")
                    SwitchRow(
                        title = "Optimización automática",
                        subtitle = "Se ejecuta en segundo plano con WorkManager",
                        checked = vm.autoEnabled,
                    ) { value ->
                        vm.updateAuto(value)
                        if (value) askNotifications()
                    }
                    if (vm.autoEnabled) {
                        Text(
                            "Frecuencia",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        ChoiceRow(
                            options = listOf(12 to "Cada 12 h", 24 to "Diaria", 72 to "Cada 3 días", 168 to "Semanal"),
                            selected = vm.interval,
                            onSelect = vm::updateInterval,
                        )
                        SwitchRow(
                            title = "Solo mientras se carga",
                            subtitle = "Así no gasta batería",
                            checked = vm.onlyCharging,
                            onChange = vm::updateOnlyCharging,
                        )
                        SwitchRow(
                            title = "Eliminar archivos basura",
                            subtitle = if (hasFiles) "Temporales, miniaturas y carpetas vacías" else "Requiere acceso a archivos",
                            checked = vm.cleanJunk && hasFiles,
                        ) { value ->
                            if (value && !hasFiles) requestFiles() else vm.updateCleanJunk(value)
                        }
                        SwitchRow(
                            title = "Avisarme del resultado",
                            subtitle = null,
                            checked = vm.notifyResults,
                        ) { value ->
                            vm.updateNotify(value)
                            if (value) askNotifications()
                        }
                    }
                }
            }

            // ---------------- Alertas ----------------
            item { SectionTitle("Alertas") }
            item {
                NCard {
                    CardHeader(Icons.Rounded.Notifications, accents.cache, "Avisos preventivos",
                        "Nítido revisa el dispositivo una vez al día y te avisa solo si hace falta.")
                    SwitchRow(
                        title = "Aviso de poco espacio",
                        subtitle = "Cuando el espacio libre baje del límite",
                        checked = vm.lowStorage,
                    ) { value ->
                        vm.updateLowStorage(value)
                        if (value) askNotifications()
                    }
                    if (vm.lowStorage) {
                        ChoiceRow(
                            options = listOf(5 to "5 %", 10 to "10 %", 15 to "15 %", 20 to "20 %"),
                            selected = vm.threshold,
                            onSelect = vm::updateThreshold,
                        )
                    }
                    SwitchRow(
                        title = "Recordatorio de apps sin usar",
                        subtitle = if (hasUsage) "Como máximo una vez por semana" else "Requiere acceso a datos de uso",
                        checked = vm.unusedReminder,
                    ) { value ->
                        vm.updateUnusedReminder(value)
                        if (value) {
                            askNotifications()
                            if (!hasUsage) requestUsage()
                        }
                    }
                    if (!hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = askNotifications, modifier = Modifier.fillMaxWidth()) {
                            Text("Activar notificaciones")
                        }
                    }
                }
            }

            // ---------------- Apps sin usar ----------------
            item { SectionTitle("Apps que no usas") }
            item {
                NCard {
                    CardHeader(Icons.Rounded.Apps, accents.memory, "Sin abrir en 30 días",
                        "Desinstalarlas libera espacio y reduce procesos en segundo plano.")
                    val list = vm.unused
                    when {
                        !hasUsage -> FilledTonalButton(
                            onClick = { requestUsage() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Permitir acceso de uso") }
                        vm.loadingUnused -> LoadingRow("Analizando tus apps…")
                        list == null -> FilledTonalButton(onClick = vm::loadUnused, modifier = Modifier.fillMaxWidth()) {
                            Text("Buscar apps sin usar")
                        }
                        list.isEmpty() -> Text("¡Bien! Usas todas tus apps.", style = MaterialTheme.typography.bodyMedium)
                        else -> Text(
                            "${list.size} apps · ${Fmt.bytes(list.sumOf { it.sizeBytes })} en total",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            val unusedList = vm.unused
            if (hasUsage && !unusedList.isNullOrEmpty()) {
                items(unusedList, key = { "unused:${it.packageName}" }) { app ->
                    UnusedAppRow(app) { openSettings(Intents.uninstall(app.packageName)) }
                }
            }

            // ---------------- Rendimiento visual ----------------
            item { SectionTitle("Rendimiento visual") }
            item {
                NCard {
                    CardHeader(Icons.Rounded.SlowMotionVideo, accents.battery, "Velocidad de las animaciones",
                        "Acortarlas hace que abrir y cambiar de app se sienta más ágil.")
                    if (vm.canTweak) {
                        ChoiceRow(
                            options = listOf(1f to "Normal", 0.5f to "Rápidas", 0f to "Sin animaciones"),
                            selected = vm.animationScale,
                            onSelect = { scale ->
                                if (!vm.updateAnimationScale(scale)) {
                                    Toast.makeText(context, "Android no permitió el cambio", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    } else {
                        Text(
                            "Android solo permite cambiarlas a apps autorizadas. Autoriza a Nítido una sola vez " +
                                "desde un ordenador con ADB:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val command = SystemTweaks.grantCommand(context)
                        CommandRow(command, null) { copy(command) }
                        TextButton(onClick = { tryOpen(Intents.developerOptions()) }) {
                            Text("O cámbialas a mano en Opciones de desarrollador")
                        }
                    }
                }
            }

            // ---------------- Accesos directos ----------------
            item { SectionTitle("Ajustes recomendados de Android") }
            item {
                NCard(padding = 12.dp) {
                    ShortcutRow(Icons.Rounded.SdStorage, "Gestor de almacenamiento", "Libera espacio con las herramientas del sistema") {
                        if (!openSettings(Intents.storageManager())) tryOpen(Intents.storageSettings())
                    }
                    ShortcutRow(Icons.Rounded.BatteryAlert, "Uso de batería", "Descubre qué apps consumen más") {
                        tryOpen(Intents.batteryUsage())
                    }
                    ShortcutRow(Icons.Rounded.Speed, "Optimización de batería", "Restringe las apps que trabajan en segundo plano") {
                        tryOpen(Intents.batteryOptimization())
                    }
                    ShortcutRow(Icons.Rounded.DeveloperMode, "Opciones de desarrollador", "Animaciones y límite de procesos") {
                        tryOpen(Intents.developerOptions())
                    }
                    ShortcutRow(Icons.Rounded.Storage, "Almacenamiento", "Resumen de espacio por categorías") {
                        tryOpen(Intents.storageSettings())
                    }
                }
            }

            // ---------------- Consejos ----------------
            item { SectionTitle("Consejos para un dispositivo estable") }
            item {
                NCard {
                    val upDays = DeviceMonitor.uptimeDays()
                    if (upDays >= 3) {
                        InfoNote("Tu dispositivo lleva $upDays días encendido. Un reinicio semanal ayuda a mantenerlo fluido.")
                        Spacer(Modifier.height(8.dp))
                    }
                    Bullet("Mantén al menos un 15 % de almacenamiento libre: con el disco lleno, Android se ralentiza.")
                    Bullet("Instala las actualizaciones del sistema y de las apps: corrigen fallos y mejoran el rendimiento.")
                    Bullet("Reinicia el teléfono una vez por semana para limpiar la memoria y los procesos atascados.")
                    Bullet("No cierres apps a la fuerza constantemente: Android las vuelve a abrir y gasta más batería.")
                    Bullet("Desinstala lo que no uses y evita fondos de pantalla animados y widgets que no necesitas.")
                    Bullet("Evita el calor: reduce la velocidad del procesador y desgasta la batería.")
                }
            }

            // ---------------- Avanzado ----------------
            item { SectionTitle("Avanzado: comandos ADB (sin root)") }
            item {
                NCard {
                    CardHeader(Icons.Rounded.Code, accents.info, "Optimización desde un ordenador",
                        "Con la depuración USB activada puedes aplicar ajustes que Android no permite a las apps.")
                    for ((command, description) in SystemTweaks.adbCommands) {
                        CommandRow(command, description) { copy(command) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(8.dp))
}
