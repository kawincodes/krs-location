package com.krstechsolutions.krslocation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.ui.components.ButtonIcon
import com.krstechsolutions.krslocation.ui.components.ButtonSize
import com.krstechsolutions.krslocation.ui.components.ButtonVariant
import com.krstechsolutions.krslocation.ui.components.ShadButton
import com.krstechsolutions.krslocation.ui.components.ShadListRow
import com.krstechsolutions.krslocation.ui.theme.ShadRadius
import com.krstechsolutions.krslocation.ui.theme.ShadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(vm: MainViewModel) {
    val c = ShadTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.showFavorites = false },
        sheetState = sheetState,
        containerColor = c.card,
        contentColor = c.cardForeground,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(c.muted, ShadRadius.fullShape),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.favorites_title), style = ShadTheme.typography.h4, modifier = Modifier.weight(1f))
                ShadButton(onClick = vm::openSaveDialog, variant = ButtonVariant.Outline, size = ButtonSize.Sm) {
                    ButtonIcon(Lucide.Star)
                    Text(stringResource(R.string.favorites_save_current))
                }
            }

            if (vm.favorites.isEmpty()) {
                Text(
                    stringResource(R.string.favorites_empty),
                    style = ShadTheme.typography.muted,
                    color = c.mutedForeground,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(vm.favorites, key = { it.id }) { fav ->
                        ShadListRow(
                            title = fav.name,
                            subtitle = fav.point.format(),
                            icon = Lucide.Star,
                            onClick = { vm.useFavorite(fav) },
                            trailing = {
                                ShadButton(
                                    onClick = { vm.deleteFavorite(fav.id) },
                                    variant = ButtonVariant.Ghost,
                                    size = ButtonSize.IconSm,
                                ) {
                                    Icon(Lucide.Trash2, stringResource(R.string.action_delete), Modifier.size(16.dp), tint = c.mutedForeground)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
