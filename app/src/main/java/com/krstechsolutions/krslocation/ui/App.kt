package com.krstechsolutions.krslocation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldAlert
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.Screen
import com.krstechsolutions.krslocation.ui.components.ShadDialog
import com.krstechsolutions.krslocation.update.UpdateInstaller

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

    // Overlays shared by both screens.
    vm.releaseNotesFor?.let { release ->
        ReleaseNotesSheet(vm = vm, release = release, mode = vm.releaseNotesMode)
    }
    if (vm.showReleaseHistory) {
        ReleaseHistorySheet(vm)
    }
    val status = vm.installStatus
    if (status is UpdateInstaller.Status.SignatureMismatch) {
        val context = LocalContext.current
        ShadDialog(
            onDismissRequest = vm::dismissInstallStatus,
            icon = Lucide.ShieldAlert,
            title = stringResource(R.string.update_signature_title),
            description = stringResource(
                if (status.installedIsDebug) R.string.update_signature_message_debug else R.string.update_signature_message
            ),
            confirmText = stringResource(R.string.action_uninstall),
            onConfirm = {
                vm.dismissInstallStatus()
                UpdateInstaller.uninstallSelf(context)
            },
            cancelText = stringResource(R.string.action_cancel),
        )
    }
}
