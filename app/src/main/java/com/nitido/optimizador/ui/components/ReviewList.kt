package com.nitido.optimizador.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitido.optimizador.core.Category
import com.nitido.optimizador.core.Fmt
import com.nitido.optimizador.core.ScanItem
import com.nitido.optimizador.core.StoragePaths
import com.nitido.optimizador.ui.ReviewState
import com.nitido.optimizador.ui.theme.LocalAccents
import java.util.Locale

/** Añade a una LazyColumn las categorías encontradas, con sus archivos al desplegarlas. */
fun LazyListScope.reviewItems(state: ReviewState, maxPerCategory: Int = 200) {
    val result = state.result ?: return
    for (category in Category.entries) {
        val list = result.items(category)
        if (list.isEmpty()) continue
        item(key = "cat:${category.name}") {
            CategoryHeader(
                category = category,
                count = list.size,
                bytes = result.size(category),
                toggle = state.categoryState(category),
                expanded = category in state.expanded,
                onToggle = { state.toggleCategory(category) },
                onExpand = { state.toggleExpanded(category) },
            )
        }
        if (category in state.expanded) {
            val shown = if (list.size > maxPerCategory) list.take(maxPerCategory) else list
            items(shown, key = { "file:${it.path}" }) { entry ->
                FileRow(entry, checked = entry.path in state.selected, onToggle = { state.toggle(entry) })
            }
            if (list.size > maxPerCategory) {
                item(key = "more:${category.name}") {
                    Text(
                        "y ${list.size - maxPerCategory} más (se incluyen al marcar la categoría)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: Category,
    count: Int,
    bytes: Long,
    toggle: ToggleableState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
) {
    val color = categoryColor(category)
    Surface(
        onClick = onExpand,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(categoryIcon(category), color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleSmall)
                val amount = if (category == Category.EMPTY_FOLDERS) {
                    "$count carpetas"
                } else {
                    "$count elementos · ${Fmt.bytes(bytes)}"
                }
                Text(amount, style = MaterialTheme.typography.bodyMedium, color = color)
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Ocultar archivos" else "Ver archivos",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TriStateCheckbox(state = toggle, onClick = onToggle)
        }
    }
}

@Composable
private fun FileRow(item: ScanItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            fileIcon(item),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = buildString {
                if (item.group >= 0) append("Grupo ${item.group + 1} · ")
                append(StoragePaths.folderOf(item.path))
                item.note?.let { append(" · ").append(it) }
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.isDir) {
            Spacer(Modifier.width(8.dp))
            Text(Fmt.bytes(item.size), style = MaterialTheme.typography.labelMedium)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun categoryColor(category: Category): Color {
    val accents = LocalAccents.current
    return when (category) {
        Category.TEMP -> accents.cache
        Category.THUMBNAILS -> accents.info
        Category.EMPTY_FOLDERS -> accents.storage
        Category.APKS -> accents.good
        Category.DUPLICATES -> accents.memory
        Category.LARGE -> accents.danger
        Category.OLD_DOWNLOADS -> accents.battery
    }
}

private fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.TEMP -> Icons.Rounded.DeleteSweep
    Category.THUMBNAILS -> Icons.Rounded.Image
    Category.EMPTY_FOLDERS -> Icons.Rounded.FolderOpen
    Category.APKS -> Icons.Rounded.Android
    Category.DUPLICATES -> Icons.Rounded.FileCopy
    Category.LARGE -> Icons.Rounded.SdStorage
    Category.OLD_DOWNLOADS -> Icons.Rounded.FileDownload
}

private fun fileIcon(item: ScanItem): ImageVector {
    if (item.isDir) return Icons.Rounded.FolderOpen
    return when (item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> Icons.Rounded.Image
        "mp4", "mkv", "mov", "avi", "3gp", "webm" -> Icons.Rounded.Movie
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> Icons.Rounded.MusicNote
        "zip", "rar", "7z", "tar", "gz" -> Icons.Rounded.Archive
        "apk", "apks", "xapk", "apkm" -> Icons.Rounded.Android
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
}
