package com.nitido.optimizador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nitido.optimizador.ui.screens.CacheScreen
import com.nitido.optimizador.ui.screens.DeepScreen
import com.nitido.optimizador.ui.screens.HomeScreen
import com.nitido.optimizador.ui.screens.MaintenanceScreen
import com.nitido.optimizador.ui.screens.MemoryScreen
import com.nitido.optimizador.ui.screens.QuickScreen
import com.nitido.optimizador.ui.screens.StorageScreen

/** Pantallas de la app. */
enum class Dest { HOME, QUICK, DEEP, CACHE, STORAGE, MEMORY, MAINTENANCE }

/** Navegación sencilla entre pantallas con animación y botón atrás. */
@Composable
fun NitidoRoot() {
    var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
    val goHome: () -> Unit = { dest = Dest.HOME }

    BackHandler(enabled = dest != Dest.HOME) { goHome() }

    AnimatedContent(
        targetState = dest,
        transitionSpec = {
            val forward = targetState != Dest.HOME
            val enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { width ->
                if (forward) width / 8 else -width / 8
            }
            val exit = fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { width ->
                if (forward) -width / 8 else width / 8
            }
            enter togetherWith exit
        },
        label = "navegacion",
    ) { screen ->
        when (screen) {
            Dest.HOME -> HomeScreen(onNavigate = { dest = it })
            Dest.QUICK -> QuickScreen(onBack = goHome, onDeep = { dest = Dest.DEEP })
            Dest.DEEP -> DeepScreen(onBack = goHome)
            Dest.CACHE -> CacheScreen(onBack = goHome)
            Dest.STORAGE -> StorageScreen(onBack = goHome)
            Dest.MEMORY -> MemoryScreen(onBack = goHome)
            Dest.MAINTENANCE -> MaintenanceScreen(onBack = goHome)
        }
    }
}
