package com.nitido.optimizador.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitido.optimizador.core.AppCache
import com.nitido.optimizador.core.AppEntry
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.ui.theme.LocalAccents

/** Fila de una app con su caché. Al tocarla se abren sus ajustes para pulsar "Borrar caché". */
@Composable
fun AppCacheRow(app: AppCache, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Ocupa ${Fmt.bytes(app.totalBytes)} en total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Fmt.bytes(app.cacheBytes),
                style = MaterialTheme.typography.titleSmall,
                color = LocalAccents.current.cache,
            )
            Text("Limpiar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Fila de una app que no se usa, con botón para desinstalarla (el sistema pide confirmación). */
@Composable
fun UnusedAppRow(app: AppEntry, onUninstall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val used = if (app.lastUsed > 0) "Último uso: ${Fmt.ago(app.lastUsed)}" else "Sin uso registrado"
            val size = if (app.sizeBytes > 0) " · ${Fmt.bytes(app.sizeBytes)}" else ""
            Text(
                used + size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onUninstall, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text("Desinstalar")
        }
    }
}

/** Fila de una app usada recientemente, con acceso a "Forzar detención". */
@Composable
fun RecentAppRow(app: AppEntry, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Usada ${Fmt.ago(app.lastUsed)} · ${Fmt.minutes(app.foregroundMs)} en 24 h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onStop) { Text("Detener") }
    }
}
