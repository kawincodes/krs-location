package com.krstechsolutions.krslocation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.Screen

/** Root of the Compose UI: a two-screen app with back handling, no navigation library needed. */
@Composable
fun KrsApp(vm: MainViewModel) {
    BackHandler(enabled = vm.screen != Screen.Home) { vm.screen = Screen.Home }
    BackHandler(enabled = vm.screen == Screen.Home && vm.searchOpen) { vm.closeSearch() }

    Crossfade(targetState = vm.screen, label = "screen") { screen ->
        when (screen) {
            Screen.Home -> MainScreen(vm)
            Screen.About -> AboutScreen(vm)
        }
    }
}
