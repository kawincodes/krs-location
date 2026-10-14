package com.krstechsolutions.krslocation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Footprints
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.TriangleAlert
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.MockLocationService
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.ui.components.AlertVariant
import com.krstechsolutions.krslocation.ui.components.BadgeVariant
import com.krstechsolutions.krslocation.ui.components.ButtonIcon
import com.krstechsolutions.krslocation.ui.components.ButtonSize
import com.krstechsolutions.krslocation.ui.components.ButtonVariant
import com.krstechsolutions.krslocation.ui.components.ShadAlert
import com.krstechsolutions.krslocation.ui.components.ShadBadge
import com.krstechsolutions.krslocation.ui.components.ShadButton
import com.krstechsolutions.krslocation.ui.components.ShadCard
import com.krstechsolutions.krslocation.ui.components.ShadDialog
import com.krstechsolutions.krslocation.ui.components.ShadInput
import com.krstechsolutions.krslocation.ui.components.ShadSeparator
import com.krstechsolutions.krslocation.ui.components.ShadSwitch
import com.krstechsolutions.krslocation.ui.map.OsmMap
import com.krstechsolutions.krslocation.ui.theme.ShadRadius
import com.krstechsolutions.krslocation.ui.theme.ShadTheme
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.serviceState.observeAsState(MockLocationService.State())
    val focusManager = LocalFocusManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> vm.onPermissionsResult(result) }

    LifecycleResumeEffect(Unit) {
        vm.refreshMockAppStatus()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                isMockApp = vm.isMockLocationApp,
                modifier = Modifier.statusBarsPadding(),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MapArea(vm = vm, state = state)
            }

            ControlPanel(
                vm = vm,
                state = state,
                focusManager = focusManager,
                onStartClick = {
                    focusManager.clearFocus()
                    val needed = vm.requestStart()
                    if (needed.isNotEmpty()) permissionLauncher.launch(needed)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.15f),
            )
        }

        ToastHost(
            message = vm.message,
            onDismiss = vm::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )
    }

    if (vm.showSetupDialog) {
        ShadDialog(
            onDismissRequest = { vm.showSetupDialog = false },
            icon = Lucide.Settings2,
            title = stringResource(R.string.dialog_mock_title),
            description = stringResource(R.string.dialog_mock_message),
            confirmText = stringResource(R.string.dialog_open_settings),
            onConfirm = vm::openDeveloperOptions,
            cancelText = stringResource(R.string.dialog_cancel),
        )
    }
}

// ---- Top bar --------------------------------------------------------------------------------

@Composable
private fun TopBar(state: MockLocationService.State, isMockApp: Boolean, modifier: Modifier = Modifier) {
    val c = ShadTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(ShadRadius.lgShape)
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.MapPin, null, Modifier.size(18.dp), tint = c.primaryForeground)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = ShadTheme.typography.h4)
            Text(
                stringResource(R.string.app_subtitle),
                style = ShadTheme.typography.muted,
                color = c.mutedForeground,
            )
        }
        when {
            state.running && state.moving -> ShadBadge(
                stringResource(R.string.badge_walking),
                variant = BadgeVariant.Success,
                leading = { PulsingDot(c.successForeground) },
            )
            state.running -> ShadBadge(
                stringResource(R.string.badge_active),
                variant = BadgeVariant.Success,
                leading = { PulsingDot(c.successForeground) },
            )
            isMockApp -> ShadBadge(
                stringResource(R.string.badge_ready),
                variant = BadgeVariant.Secondary,
                leading = { Icon(Lucide.ShieldCheck, null, Modifier.size(12.dp)) },
            )
            else -> ShadBadge(stringResource(R.string.badge_off), variant = BadgeVariant.Outline)
        }
    }
    ShadSeparator()
}

@Composable
private fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

// ---- Map ------------------------------------------------------------------------------------

@Composable
private fun BoxScope.MapArea(vm: MainViewModel, state: MockLocationService.State) {
    val c = ShadTheme.colors

    // While walking, the pin shows where the device currently "is", not the destination.
    val markerPosition = if (state.running && state.moving) {
        GeoPoint(state.latitude, state.longitude)
    } else {
        vm.target
    }

    OsmMap(
        markerPosition = markerPosition,
        cameraRequest = vm.cameraRequest,
        initialZoom = vm.initialZoom,
        darkTheme = c.isDark,
        onTap = { vm.setTarget(it, recenter = false) },
        onMarkerDragEnd = { vm.setTarget(it, recenter = false) },
        onZoomChanged = vm::saveZoom,
        modifier = Modifier.fillMaxSize(),
    )

    // Hint chip
    MapChip(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp),
    ) {
        Icon(Lucide.MapPin, null, Modifier.size(12.dp), tint = c.mutedForeground)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.hint_tap_map), style = ShadTheme.typography.xs, color = c.mutedForeground)
    }

    // Live coordinates
    MapChip(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(12.dp),
    ) {
        Text(
            MainViewModel.formatPoint(markerPosition.latitude, markerPosition.longitude),
            style = ShadTheme.typography.mono,
        )
    }

    // Recenter
    ShadButton(
        onClick = vm::recenter,
        variant = ButtonVariant.Outline,
        size = ButtonSize.Icon,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp)
            .shadow(2.dp, ShadRadius.mdShape),
    ) {
        Icon(Lucide.LocateFixed, stringResource(R.string.action_center), Modifier.size(18.dp))
    }
}

@Composable
private fun MapChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = ShadTheme.colors
    Row(
        modifier
            .shadow(2.dp, ShadRadius.fullShape)
            .clip(ShadRadius.fullShape)
            .background(c.card.copy(alpha = 0.92f))
            .border(1.dp, c.border, ShadRadius.fullShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

// ---- Controls -------------------------------------------------------------------------------

@Composable
private fun ControlPanel(
    vm: MainViewModel,
    state: MockLocationService.State,
    focusManager: FocusManager,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ShadTheme.colors
    Column(
        modifier
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!vm.isMockLocationApp) {
            ShadAlert(
                title = stringResource(R.string.status_mock_app_missing),
                description = stringResource(R.string.status_mock_app_missing_desc),
                icon = Lucide.TriangleAlert,
                variant = AlertVariant.Destructive,
                action = {
                    ShadButton(
                        onClick = vm::openDeveloperOptions,
                        variant = ButtonVariant.Outline,
                        size = ButtonSize.Sm,
                    ) {
                        ButtonIcon(Lucide.Settings2)
                        Text(stringResource(R.string.action_developer_options))
                    }
                },
            )
        }

        StatusCard(state = state, target = vm.target)

        PositionSection(vm = vm, focusManager = focusManager)

        if (state.running) {
            ShadButton(
                onClick = vm::stopMocking,
                variant = ButtonVariant.Destructive,
                size = ButtonSize.Lg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonIcon(Lucide.Square)
                Text(stringResource(R.string.action_stop_mocking))
            }
        } else {
            ShadButton(
                onClick = onStartClick,
                size = ButtonSize.Lg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonIcon(Lucide.Play)
                Text(stringResource(R.string.action_start))
            }
        }

        ShadSeparator(Modifier.padding(vertical = 4.dp))

        MovementSection(vm = vm, focusManager = focusManager)

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun StatusCard(state: MockLocationService.State, target: GeoPoint) {
    val c = ShadTheme.colors
    ShadCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val (icon, tint) = when {
                state.running && state.moving -> Lucide.Footprints to c.success
                state.running -> Lucide.Navigation to c.success
                else -> Lucide.Navigation to c.mutedForeground
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(ShadRadius.lgShape)
                    .background(c.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = tint)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        state.running && state.moving -> stringResource(R.string.status_moving)
                        state.running -> stringResource(R.string.status_running)
                        else -> stringResource(R.string.status_idle)
                    },
                    style = ShadTheme.typography.small,
                )
                Text(
                    when {
                        state.running && state.moving -> MainViewModel.formatPoint(target.latitude, target.longitude)
                        state.running -> MainViewModel.formatPoint(state.latitude, state.longitude)
                        else -> stringResource(R.string.status_idle_desc)
                    },
                    style = if (state.running) ShadTheme.typography.mono else ShadTheme.typography.muted,
                    color = c.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun PositionSection(vm: MainViewModel, focusManager: FocusManager) {
    SectionLabel(stringResource(R.string.section_position))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ShadInput(
            value = vm.latitudeText,
            onValueChange = { vm.latitudeText = it },
            label = stringResource(R.string.hint_latitude),
            placeholder = "28.612912",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        ShadInput(
            value = vm.longitudeText,
            onValueChange = { vm.longitudeText = it },
            label = stringResource(R.string.hint_longitude),
            placeholder = "77.229510",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (vm.applyTypedCoordinates()) focusManager.clearFocus()
            }),
            modifier = Modifier.weight(1f),
        )
        ShadButton(
            onClick = {
                if (vm.applyTypedCoordinates()) focusManager.clearFocus()
            },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Icon,
        ) {
            Icon(Lucide.Check, stringResource(R.string.action_apply), Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ColumnScope.MovementSection(vm: MainViewModel, focusManager: FocusManager) {
    val c = ShadTheme.colors
    SectionLabel(stringResource(R.string.section_movement))

    // Walk-mode switch row
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShadRadius.lgShape)
            .border(1.dp, c.border, ShadRadius.lgShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Lucide.Footprints, null, Modifier.size(18.dp), tint = c.mutedForeground)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.label_smooth), style = ShadTheme.typography.small)
            Text(stringResource(R.string.label_smooth_desc), style = ShadTheme.typography.muted, color = c.mutedForeground)
        }
        ShadSwitch(checked = vm.smoothMovement, onCheckedChange = vm::setSmoothMovement)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShadInput(
            value = vm.speedText,
            onValueChange = { vm.speedText = it },
            label = stringResource(R.string.hint_speed),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        ShadInput(
            value = vm.stepText,
            onValueChange = { vm.stepText = it },
            label = stringResource(R.string.hint_step),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                vm.commitMovementInputs()
                focusManager.clearFocus()
            }),
            modifier = Modifier.weight(1f),
        )
    }

    // Direction pad
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.label_nudge), style = ShadTheme.typography.small)
            Text(stringResource(R.string.label_nudge_desc), style = ShadTheme.typography.muted, color = c.mutedForeground)
        }
        NudgePad(vm = vm, onBeforeNudge = { focusManager.clearFocus() })
    }
}

@Composable
private fun NudgePad(vm: MainViewModel, onBeforeNudge: () -> Unit) {
    val c = ShadTheme.colors
    val gap = 6.dp

    @Composable
    fun PadButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, bearing: Double) {
        ShadButton(
            onClick = { onBeforeNudge(); vm.nudge(bearing) },
            variant = ButtonVariant.Outline,
            size = ButtonSize.Icon,
        ) {
            Icon(icon, description, Modifier.size(18.dp))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
        PadButton(Lucide.ArrowUp, stringResource(R.string.action_north), 0.0)
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            PadButton(Lucide.ArrowLeft, stringResource(R.string.action_west), 270.0)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(ShadRadius.mdShape)
                    .background(c.muted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${vm.stepText}m",
                    style = ShadTheme.typography.xs.copy(fontWeight = FontWeight.SemiBold),
                    color = c.mutedForeground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            PadButton(Lucide.ArrowRight, stringResource(R.string.action_east), 90.0)
        }
        PadButton(Lucide.ArrowDown, stringResource(R.string.action_south), 180.0)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = ShadTheme.typography.xs.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
        color = ShadTheme.colors.mutedForeground,
    )
}

// ---- Toast ----------------------------------------------------------------------------------

@Composable
private fun ToastHost(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = ShadTheme.colors
    LaunchedEffect(message) {
        if (message != null) {
            delay(4500)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.padding(16.dp),
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        // Keep the last text while the exit animation plays.
        var shown by remember { mutableStateOf(message ?: "") }
        if (message != null) shown = message
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, ShadRadius.lgShape)
                .clip(ShadRadius.lgShape)
                .background(c.popover)
                .border(1.dp, c.border, ShadRadius.lgShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Lucide.TriangleAlert, null, Modifier.size(16.dp), tint = c.popoverForeground)
            Text(shown, style = ShadTheme.typography.p, color = c.popoverForeground, modifier = Modifier.weight(1f))
        }
    }
}
