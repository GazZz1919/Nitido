package com.nitido.optimizador.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState
import com.nitido.optimizador.core.Category
import com.nitido.optimizador.core.DeleteResult
import com.nitido.optimizador.core.ScanItem
import com.nitido.optimizador.core.ScanResult
import com.nitido.optimizador.core.StorageScanner

/**
 * Estado de la lista de revisión de archivos (selección, categorías abiertas y borrado).
 * Lo comparten la pantalla de liberar espacio y la optimización profunda.
 */
class ReviewState {

    var result by mutableStateOf<ScanResult?>(null)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var expanded by mutableStateOf<Set<Category>>(emptySet())
        private set
    var deleting by mutableStateOf(false)
        private set
    var deleteProgress by mutableStateOf(0f)
        private set

    val selectedItems: List<ScanItem> by derivedStateOf {
        val current = result
        val chosen = selected
        current?.all?.filter { it.path in chosen }.orEmpty()
    }

    val selectedBytes: Long by derivedStateOf { selectedItems.sumOf { it.size } }

    fun clear() {
        result = null
        selected = emptySet()
        expanded = emptySet()
    }

    /** Carga un resultado y marca lo que es seguro borrar. */
    fun load(newResult: ScanResult) {
        result = newResult
        selected = newResult.all.filter { it.preselect }.mapTo(HashSet()) { it.path }
        expanded = emptySet()
    }

    fun toggle(item: ScanItem) {
        selected = if (item.path in selected) selected - item.path else selected + item.path
    }

    fun toggleExpanded(category: Category) {
        expanded = if (category in expanded) expanded - category else expanded + category
    }

    /** En duplicados, "marcar todo" deja sin marcar el original de cada grupo. */
    private fun selectable(category: Category): List<ScanItem> {
        val items = result?.items(category).orEmpty()
        return if (category == Category.DUPLICATES) items.filterNot { it.keep } else items
    }

    fun categoryState(category: Category): ToggleableState {
        val items = result?.items(category).orEmpty()
        val candidates = selectable(category)
        if (candidates.isEmpty()) return ToggleableState.Off
        val anySelected = items.any { it.path in selected }
        val allCandidates = candidates.all { it.path in selected }
        return when {
            !anySelected -> ToggleableState.Off
            allCandidates -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    }

    fun toggleCategory(category: Category) {
        val all = result?.items(category).orEmpty().map { it.path }
        selected = if (categoryState(category) == ToggleableState.On) {
            selected - all.toSet()
        } else {
            selected + selectable(category).map { it.path }
        }
    }

    /** Borra lo seleccionado y actualiza la lista. Devuelve null si no había nada que borrar. */
    suspend fun deleteSelected(scanner: StorageScanner): DeleteResult? {
        val items = selectedItems
        if (items.isEmpty() || deleting) return null
        deleting = true
        deleteProgress = 0f
        try {
            val res = scanner.delete(items) { progress -> deleteProgress = progress }
            result = result?.without(res.deletedPaths)
            selected = selected - res.deletedPaths
            return res
        } finally {
            deleting = false
        }
    }
}
