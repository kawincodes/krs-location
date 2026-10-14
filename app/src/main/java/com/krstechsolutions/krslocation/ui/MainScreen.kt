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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Footprints
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.Waypoints
import com.composables.icons.lucide.X
import com.krstechsolutions.krslocation.EditMode
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.MockLocationService
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.Screen
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
import com.krstechsolutions.krslocation.ui.components.ShadInputDialog
import com.krstechsolutions.krslocation.ui.components.ShadListRow
import com.krstechsolutions.krslocation.ui.components.ShadProgress
import com.krstechsolutions.krslocation.ui.components.ShadSeparator
import com.krstechsolutions.krslocation.ui.components.ShadSwitch
import com.krstechsolutions.krslocation.ui.components.ShadTabs
import com.krstechsolutions.krslocation.ui.map.OsmMap
import com.krstechsolutions.krslocation.ui.theme.ShadRadius
import com.krstechsolutions.krslocation.ui.theme.ShadTheme
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.serviceState.observeAsState(MockLocationService.State())
    val focusManager = LocalFocusManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> vm.onPermissionsResult(result) }
    val requestPermissions: (Array<String>) -> Unit = { if (it.isNotEmpty()) permissionLauncher.launch(it) }

    LifecycleResumeEffect(Unit) {
        vm.refreshMockAppStatus()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                isMockApp = vm.isMockLocationApp,
                searchOpen = vm.searchOpen,
                onSearch = { if (vm.searchOpen) vm.closeSearch() else vm.openSearch() },
                onFavorites = { vm.showFavorites = true },
                onAbout = { vm.screen = Screen.About },
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
                requestPermissions = requestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f),
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

    if (vm.showSaveDialog) {
        ShadInputDialog(
            onDismissRequest = { vm.showSaveDialog = false },
            title = stringResource(R.string.favorite_dialog_title),
            description = MainViewModel.formatPoint(vm.target.latitude, vm.target.longitude),
            value = vm.saveName,
            onValueChange = { vm.saveName = it },
            placeholder = stringResource(R.string.favorite_name_hint),
            confirmText = stringResource(R.string.action_save),
            onConfirm = vm::saveFavorite,
            cancelText = stringResource(R.string.action_cancel),
        )
    }

    if (vm.showFavorites) {
        FavoritesSheet(vm)
    }
}

// ---- Top bar --------------------------------------------------------------------------------

@Composable
private fun TopBar(
    state: MockLocationService.State,
    isMockApp: Boolean,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ShadTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(stringResource(R.string.app_name), style = ShadTheme.typography.h4)
            StatusBadge(state, isMockApp)
        }
        ShadButton(onClick = onSearch, variant = if (searchOpen) ButtonVariant.Secondary else ButtonVariant.Ghost, size = ButtonSize.Icon) {
            Icon(Lucide.Search, stringResource(R.string.action_search), Modifier.size(20.dp))
        }
        ShadButton(onClick = onFavorites, variant = ButtonVariant.Ghost, size = ButtonSize.Icon) {
            Icon(Lucide.Star, stringResource(R.string.action_favorites), Modifier.size(20.dp))
        }
        ShadButton(onClick = onAbout, variant = ButtonVariant.Ghost, size = ButtonSize.Icon) {
            Icon(Lucide.Info, stringResource(R.string.action_about), Modifier.size(20.dp))
        }
    }
    ShadSeparator()
}

@Composable
private fun StatusBadge(state: MockLocationService.State, isMockApp: Boolean) {
    val c = ShadTheme.colors
    val route = state.route
    when {
        state.running && route != null && route.paused -> ShadBadge(
            stringResource(R.string.badge_paused),
            variant = BadgeVariant.Secondary,
            leading = { Icon(Lucide.Pause, null, Modifier.size(10.dp)) },
        )
        state.running && route != null -> ShadBadge(
            stringResource(R.string.badge_route),
            variant = BadgeVariant.Success,
            leading = { PulsingDot(c.successForeground) },
        )
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
    val routePlaying = state.running && state.route != null

    // While walking or playing a route, the pin shows where the device currently "is".
    val livePoint = if (state.running && (state.moving || routePlaying)) GeoPoint(state.latitude, state.longitude) else null
    val markerPosition: GeoPoint? = when {
        livePoint != null -> livePoint
        vm.mode == EditMode.Pin -> vm.target
        else -> null
    }
    val routePoints = remember(vm.waypoints.toList(), vm.playingRoute, routePlaying) {
        (if (routePlaying && vm.playingRoute.isNotEmpty()) vm.playingRoute else vm.waypoints.toList()).map { it.toGeoPoint() }
    }
    val showRoute = vm.mode == EditMode.Route || routePlaying
    val traveled = if (routePlaying) vm.traveledPoints(state) else emptyList()

    OsmMap(
        markerPosition = markerPosition,
        routePoints = if (showRoute) routePoints else emptyList(),
        traveledPoints = traveled,
        routeColor = c.mutedForeground.copy(alpha = 0.75f),
        traveledColor = if (c.isDark) Color(0xFF22C55E) else Color(0xFF16A34A),
        cameraRequest = vm.cameraRequest,
        initialCenter = vm.target,
        initialZoom = vm.initialZoom,
        darkTheme = c.isDark,
        onTap = vm::onMapTap,
        onMarkerDragEnd = { if (vm.mode == EditMode.Pin && !routePlaying) vm.setTarget(it, recenter = false) },
        onWaypointTap = { if (!routePlaying) vm.removeWaypoint(it) },
        onZoomChanged = vm::saveZoom,
        modifier = Modifier.fillMaxSize(),
    )

    if (vm.searchOpen) {
        SearchOverlay(
            vm = vm,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    } else {
        MapChip(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
        ) {
            Icon(
                if (vm.mode == EditMode.Route) Lucide.Waypoints else Lucide.MapPin,
                null, Modifier.size(12.dp), tint = c.mutedForeground,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (vm.mode == EditMode.Route) R.string.hint_tap_route else R.string.hint_tap_map),
                style = ShadTheme.typography.xs,
                color = c.mutedForeground,
            )
        }
    }

    // Live coordinates
    val shown = livePoint ?: vm.target
    MapChip(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(12.dp),
    ) {
        Text(MainViewModel.formatPoint(shown.latitude, shown.longitude), style = ShadTheme.typography.mono)
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
private fun MapChip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = ShadTheme.colors
    Row(
        modifier
            .shadow(2.dp, ShadRadius.fullShape)
            .clip(ShadRadius.fullShape)
            .background(c.card.copy(alpha = 0.94f))
            .border(1.dp, c.border, ShadRadius.fullShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun SearchOverlay(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = ShadTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.shadow(4.dp, ShadRadius.mdShape)) {
            ShadInput(
                value = vm.searchQuery,
                onValueChange = vm::onSearchQueryChange,
                placeholder = stringResource(R.string.search_hint),
                leadingIcon = Lucide.Search,
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchResults.firstOrNull()?.let(vm::selectPlace) }),
                trailing = {
                    ShadButton(onClick = vm::closeSearch, variant = ButtonVariant.Ghost, size = ButtonSize.IconSm) {
                        Icon(Lucide.X, stringResource(R.string.action_close), Modifier.size(16.dp))
                    }
                },
            )
        }
        val hasContent = vm.searching || vm.searchError || vm.searchResults.isNotEmpty() ||
            (vm.searchQuery.trim().length >= 3)
        if (hasContent) {
            ShadCard(Modifier.fillMaxWidth(), contentPadding = 6.dp) {
                when {
                    vm.searching -> Text(
                        stringResource(R.string.about_checking),
                        style = ShadTheme.typography.muted, color = c.mutedForeground,
                        modifier = Modifier.padding(10.dp),
                    )
                    vm.searchError -> Text(
                        stringResource(R.string.search_error),
                        style = ShadTheme.typography.muted, color = c.mutedForeground,
                        modifier = Modifier.padding(10.dp),
                    )
                    vm.searchResults.isEmpty() -> Text(
                        stringResource(R.string.search_no_results),
                        style = ShadTheme.typography.muted, color = c.mutedForeground,
                        modifier = Modifier.padding(10.dp),
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(vm.searchResults) { place ->
                            ShadListRow(
                                title = place.displayName.substringBefore(","),
                                subtitle = place.displayName.substringAfter(",", "").trim().ifEmpty { place.point.format(4) },
                                icon = Lucide.MapPin,
                                onClick = { vm.selectPlace(place) },
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.search_attribution),
                                style = ShadTheme.typography.xs, color = c.mutedForeground,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Controls -------------------------------------------------------------------------------

@Composable
private fun ControlPanel(
    vm: MainViewModel,
    state: MockLocationService.State,
    focusManager: FocusManager,
    requestPermissions: (Array<String>) -> Unit,
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
                    ShadButton(onClick = vm::openDeveloperOptions, variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
                        ButtonIcon(Lucide.Settings2)
                        Text(stringResource(R.string.action_developer_options))
                    }
                },
            )
        }

        vm.updateAvailable?.let { release ->
            ShadAlert(
                title = stringResource(R.string.update_banner, release.version),
                icon = Lucide.Download,
                action = {
                    ShadButton(onClick = { vm.screen = Screen.About }, variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
                        Text(stringResource(R.string.action_view))
                    }
                },
            )
        }

        ShadTabs(
            items = listOf(
                EditMode.Pin to stringResource(R.string.tab_pin),
                EditMode.Route to stringResource(R.string.tab_route),
            ),
            selected = vm.mode,
            onSelect = { focusManager.clearFocus(); vm.mode = it },
        )

        when (vm.mode) {
            EditMode.Pin -> PinPanel(vm, state, focusManager, requestPermissions)
            EditMode.Route -> RoutePanel(vm, state, focusManager, requestPermissions)
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// ---- Pin mode -------------------------------------------------------------------------------

@Composable
private fun ColumnScope.PinPanel(
    vm: MainViewModel,
    state: MockLocationService.State,
    focusManager: FocusManager,
    requestPermissions: (Array<String>) -> Unit,
) {
    val context = LocalContext.current

    StatusCard(state = state, target = vm.target)

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
            keyboardActions = KeyboardActions(onDone = { if (vm.applyTypedCoordinates()) focusManager.clearFocus() }),
            modifier = Modifier.weight(1f),
        )
        ShadButton(
            onClick = { if (vm.applyTypedCoordinates()) focusManager.clearFocus() },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Icon,
        ) {
            Icon(Lucide.Check, stringResource(R.string.action_apply), Modifier.size(18.dp))
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallAction(Lucide.ClipboardPaste, stringResource(R.string.action_paste), Modifier.weight(1f)) { vm.pasteFromClipboard() }
        SmallAction(Lucide.Share2, stringResource(R.string.action_share), Modifier.weight(1f)) { context.startActivity(vm.shareIntent()) }
        SmallAction(Lucide.Star, stringResource(R.string.action_save), Modifier.weight(1f)) { vm.openSaveDialog() }
    }

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
            onClick = { focusManager.clearFocus(); requestPermissions(vm.requestStart()) },
            size = ButtonSize.Lg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ButtonIcon(Lucide.Play)
            Text(stringResource(R.string.action_start))
        }
    }

    ShadSeparator(Modifier.padding(vertical = 4.dp))
    MovementSection(vm = vm, focusManager = focusManager)
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ShadButton(onClick = onClick, variant = ButtonVariant.Outline, size = ButtonSize.Sm, modifier = modifier) {
        ButtonIcon(icon)
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusCard(state: MockLocationService.State, target: GeoPoint) {
    val c = ShadTheme.colors
    val route = state.route
    ShadCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val (icon, tint) = when {
                state.running && route != null -> Lucide.Route to c.success
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
                        state.running && route != null && route.finished -> stringResource(R.string.route_status_done)
                        state.running && route != null && route.paused -> stringResource(R.string.route_status_paused)
                        state.running && route != null -> stringResource(R.string.route_status_playing)
                        state.running && state.moving -> stringResource(R.string.status_moving)
                        state.running -> stringResource(R.string.status_running)
                        else -> stringResource(R.string.status_idle)
                    },
                    style = ShadTheme.typography.small,
                )
                Text(
                    when {
                        state.running && route != null -> stringResource(
                            R.string.route_progress,
                            (route.fraction * 100).toInt(),
                            MainViewModel.formatDistance(route.traveledMeters),
                            MainViewModel.formatDistance(route.totalMeters),
                        )
                        state.running && state.moving -> MainViewModel.formatPoint(target.latitude, target.longitude)
                        state.running -> MainViewModel.formatPoint(state.latitude, state.longitude)
                        else -> stringResource(R.string.status_idle_desc)
                    },
                    style = if (state.running && route == null) ShadTheme.typography.mono else ShadTheme.typography.muted,
                    color = c.mutedForeground,
                )
            }
        }
        if (state.running && route != null) {
            Spacer(Modifier.height(12.dp))
            ShadProgress(route.fraction.toFloat())
        }
    }
}

@Composable
private fun ColumnScope.MovementSection(vm: MainViewModel, focusManager: FocusManager) {
    val c = ShadTheme.colors
    SectionLabel(stringResource(R.string.section_movement))

    SwitchRow(
        icon = Lucide.Footprints,
        title = stringResource(R.string.label_smooth),
        description = stringResource(R.string.label_smooth_desc),
        checked = vm.smoothMovement,
        onCheckedChange = vm::updateSmoothMovement,
    )

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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.label_nudge), style = ShadTheme.typography.small)
            Text(stringResource(R.string.label_nudge_desc), style = ShadTheme.typography.muted, color = c.mutedForeground)
        }
        NudgePad(vm = vm, onBeforeNudge = { focusManager.clearFocus() })
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = ShadTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShadRadius.lgShape)
            .border(1.dp, c.border, ShadRadius.lgShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.mutedForeground)
        Column(Modifier.weight(1f)) {
            Text(title, style = ShadTheme.typography.small)
            Text(description, style = ShadTheme.typography.muted, color = c.mutedForeground)
        }
        ShadSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NudgePad(vm: MainViewModel, onBeforeNudge: () -> Unit) {
    val c = ShadTheme.colors
    val gap = 6.dp

    @Composable
    fun PadButton(icon: ImageVector, description: String, bearing: Double) {
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

// ---- Route mode -----------------------------------------------------------------------------

@Composable
private fun ColumnScope.RoutePanel(
    vm: MainViewModel,
    state: MockLocationService.State,
    focusManager: FocusManager,
    requestPermissions: (Array<String>) -> Unit,
) {
    val c = ShadTheme.colors
    val route = state.route
    val playing = state.running && route != null
    val count = vm.waypoints.size
    val total = vm.routeTotalMeters
    val speed = vm.routeSpeedText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f

    if (playing) {
        StatusCard(state = state, target = vm.target)
    } else {
        ShadCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(ShadRadius.lgShape)
                        .background(c.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (count >= 2) Lucide.Route else Lucide.Flag, null, Modifier.size(18.dp), tint = if (count >= 2) c.foreground else c.mutedForeground)
                }
                Column(Modifier.weight(1f)) {
                    if (count < 2) {
                        Text(stringResource(R.string.route_empty_title), style = ShadTheme.typography.small)
                        Text(stringResource(R.string.route_empty_desc), style = ShadTheme.typography.muted, color = c.mutedForeground)
                    } else {
                        Text(pluralStringResource(R.plurals.route_points, count, count), style = ShadTheme.typography.small)
                        Text(
                            stringResource(
                                R.string.route_summary,
                                MainViewModel.formatDistance(total),
                                MainViewModel.formatDuration(total / speed),
                                vm.routeSpeedText,
                            ),
                            style = ShadTheme.typography.muted,
                            color = c.mutedForeground,
                        )
                    }
                }
                if (count > 0) ShadBadge("$count", variant = BadgeVariant.Secondary)
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShadButton(
            onClick = vm::undoWaypoint,
            variant = ButtonVariant.Outline,
            size = ButtonSize.Sm,
            enabled = count > 0 && !playing,
            modifier = Modifier.weight(1f),
        ) {
            ButtonIcon(Lucide.Undo2)
            Text(stringResource(R.string.action_undo))
        }
        ShadButton(
            onClick = vm::clearWaypoints,
            variant = ButtonVariant.Outline,
            size = ButtonSize.Sm,
            enabled = count > 0 && !playing,
            modifier = Modifier.weight(1f),
        ) {
            ButtonIcon(Lucide.Trash2)
            Text(stringResource(R.string.action_clear))
        }
        SmallAction(Lucide.Star, stringResource(R.string.action_save), Modifier.weight(1f)) { vm.openSaveDialog() }
    }

    // Playback controls
    if (!playing) {
        ShadButton(
            onClick = { focusManager.clearFocus(); requestPermissions(vm.requestRouteStart()) },
            size = ButtonSize.Lg,
            enabled = count >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ButtonIcon(Lucide.Play)
            Text(stringResource(R.string.action_play))
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                route!!.finished -> ShadButton(onClick = vm::resumeRoute, variant = ButtonVariant.Secondary, size = ButtonSize.Lg, modifier = Modifier.weight(1f)) {
                    ButtonIcon(Lucide.RotateCcw)
                    Text(stringResource(R.string.action_restart))
                }
                route.paused -> ShadButton(onClick = vm::resumeRoute, size = ButtonSize.Lg, modifier = Modifier.weight(1f)) {
                    ButtonIcon(Lucide.Play)
                    Text(stringResource(R.string.action_resume))
                }
                else -> ShadButton(onClick = vm::pauseRoute, variant = ButtonVariant.Secondary, size = ButtonSize.Lg, modifier = Modifier.weight(1f)) {
                    ButtonIcon(Lucide.Pause)
                    Text(stringResource(R.string.action_pause))
                }
            }
            ShadButton(onClick = vm::endRoute, variant = ButtonVariant.Destructive, size = ButtonSize.Lg, modifier = Modifier.weight(1f)) {
                ButtonIcon(Lucide.Square)
                Text(stringResource(R.string.action_stop_route))
            }
        }
    }

    ShadSeparator(Modifier.padding(vertical = 4.dp))
    SectionLabel(stringResource(R.string.section_playback))

    SwitchRow(
        icon = Lucide.Repeat,
        title = stringResource(R.string.label_loop),
        description = stringResource(R.string.label_loop_desc),
        checked = vm.routeLoop,
        onCheckedChange = vm::updateRouteLoop,
    )
    ShadInput(
        value = vm.routeSpeedText,
        onValueChange = { vm.routeSpeedText = it },
        label = stringResource(R.string.hint_route_speed),
        leadingIcon = Lucide.Footprints,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            vm.commitRouteInputs()
            focusManager.clearFocus()
        }),
    )
}

// ---- Shared bits ----------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = ShadTheme.typography.xs.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
        color = ShadTheme.colors.mutedForeground,
    )
}

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
            Icon(Lucide.Info, null, Modifier.size(16.dp), tint = c.popoverForeground)
            Text(shown, style = ShadTheme.typography.p, color = c.popoverForeground, modifier = Modifier.weight(1f))
        }
    }
}
